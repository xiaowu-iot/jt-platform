package io.github.xiaowuiot.jt.server.alarm;

import io.github.xiaowuiot.protocol.jt808.Jt808Flags;
import io.github.xiaowuiot.protocol.jt808.MessageId;
import io.github.xiaowuiot.protocol.jt808.body.AlarmExtras;
import io.github.xiaowuiot.protocol.jt808.body.AreaAlarmInfo;
import io.github.xiaowuiot.protocol.jt808.body.Location;
import io.github.xiaowuiot.jt.server.bus.UplinkBus;
import io.github.xiaowuiot.jt.server.bus.UplinkMessage;
import io.github.xiaowuiot.jt.server.config.Roles;
import io.github.xiaowuiot.jt.server.platform.DeviceStateService;
import io.github.xiaowuiot.jt.server.vehicle.VehicleCache;
import io.github.xiaowuiot.jt.server.vehicle.VehicleInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 报警识别：终端报警位按上升沿开始、下降沿结束；平台侧再判断超速和疲劳驾驶。
 * <p>
 * 只在总线线程里调用，状态不用加锁。
 * <p>
 * 平台侧判断的意义：很多终端不报超速位，或者终端里的限速参数没人去设，车辆超速了终端也不报。
 * 平台按车辆档案里的限速自己判断一遍，不依赖终端的设置。
 */
@Service
@Roles.On(Roles.PLATFORM)
public class AlarmService {

    private static final Logger log = LoggerFactory.getLogger(AlarmService.class);

    /** 超速结束要降到限速以下多少，避免在限速附近来回跳，刷出一串报警 */
    private static final double OVERSPEED_HYSTERESIS = 5;

    /** 连续驾驶超过 4 小时算疲劳驾驶；中间停车休息满 20 分钟才算重新开始 */
    private static final Duration FATIGUE_DRIVING = Duration.ofHours(4);
    private static final Duration FATIGUE_REST = Duration.ofMinutes(20);

    private final AlarmRepository repo;
    private final VehicleCache vehicles;
    private final ApplicationEventPublisher events;
    private final DeviceStateService deviceStates;
    private final Map<String, DeviceState> states = new ConcurrentHashMap<>();

    public AlarmService(AlarmRepository repo, VehicleCache vehicles, ApplicationEventPublisher events, UplinkBus bus,
                        DeviceStateService deviceStates) {
        this.deviceStates = deviceStates;
        this.repo = repo;
        this.vehicles = vehicles;
        this.events = events;
        // 上次停机时没结束的报警：平台不知道停机期间发生了什么，统一结束掉，重新上报时再开新的
        LocalDateTime now = LocalDateTime.now();
        repo.findByEndTimeIsNull().forEach(a -> {
            a.setEndTime(now);
            repo.save(a);
        });
        bus.subscribe("alarm", this::onMessage);
    }

    void onMessage(UplinkMessage m) {
        if (m.kind() == UplinkMessage.Kind.OFFLINE) {
            closeAll(m.deviceId(), LocalDateTime.now());
            return;
        }
        if (m.kind() != UplinkMessage.Kind.MESSAGE || m.msgId() != MessageId.LOCATION) {
            return;
        }
        VehicleInfo v = vehicles.byDevice(m.deviceId());
        if (v == null || v.id() == null) {
            return;
        }
        Location l = m.body();
        LocalDateTime time = l.getTime() != null ? l.getTime() : LocalDateTime.now();
        DeviceState s = states.computeIfAbsent(m.deviceId(), k -> new DeviceState());
        int serial = m.message().getHeader().getSerialNo();

        long bits = l.getAlarm();
        for (int bit = 0; bit < 32; bit++) {
            String type = AlarmTypes.terminalType(bit);
            boolean on = (bits >> bit & 1) == 1;
            if (on) {
                hold(s, v, type, Jt808Flags.alarmName(bit), AlarmTypes.terminalLevel(bit), Alarm.Source.TERMINAL,
                        time, l, serial, detail(bit, l));
            } else {
                release(s, type, time);
            }
        }

        // 平台超速
        double limit = v.speedLimit();
        if (l.getSpeed() > limit) {
            hold(s, v, AlarmTypes.PLATFORM_OVERSPEED, "平台超速", AlarmTypes.IMPORTANT, Alarm.Source.PLATFORM, time, l,
                    serial, String.format("限速 %.0f km/h，当前 %.1f km/h", limit, l.getSpeed()));
        } else if (l.getSpeed() <= limit - OVERSPEED_HYSTERESIS) {
            release(s, AlarmTypes.PLATFORM_OVERSPEED, time);
        }

        // 平台疲劳驾驶
        if (l.getSpeed() > 5) {
            if (s.drivingSince == null || (s.restSince != null && Duration.between(s.restSince, time).compareTo(FATIGUE_REST) >= 0)) {
                s.drivingSince = time;
            }
            s.restSince = null;
        } else if (s.restSince == null) {
            s.restSince = time;
        }
        boolean fatigue = s.drivingSince != null && s.restSince == null
                && Duration.between(s.drivingSince, time).compareTo(FATIGUE_DRIVING) >= 0;
        if (fatigue) {
            hold(s, v, AlarmTypes.PLATFORM_FATIGUE, "平台疲劳驾驶", AlarmTypes.IMPORTANT, Alarm.Source.PLATFORM, time, l, serial,
                    "连续驾驶 " + Duration.between(s.drivingSince, time).toMinutes() + " 分钟");
        } else if (s.restSince != null && Duration.between(s.restSince, time).compareTo(FATIGUE_REST) >= 0) {
            release(s, AlarmTypes.PLATFORM_FATIGUE, time);
        }
    }

    /**
     * 围栏、路线这些由其他模块判断的报警，也走这里统一开始和结束
     * <p>
     * l 为 null 时（比如传感器报警）用这台车最后一个位置
     */
    public void platformAlarm(String deviceId, String type, String typeName, int level, boolean on, Location l,
                              String detail) {
        VehicleInfo v = vehicles.byDevice(deviceId);
        if (v == null || v.id() == null) {
            return;
        }
        if (l == null) {
            var st = deviceStates.get(deviceId);
            l = st != null && st.getRawLocation() != null ? st.getRawLocation() : new Location().setTime(LocalDateTime.now());
        }
        DeviceState s = states.computeIfAbsent(deviceId, k -> new DeviceState());
        LocalDateTime time = l.getTime() != null ? l.getTime() : LocalDateTime.now();
        if (on) {
            hold(s, v, type, typeName, level, Alarm.Source.PLATFORM, time, l, 0, detail);
        } else {
            release(s, type, time);
        }
    }

    /**
     * 一次性的报警（主动安全的 ADAS、DSM 报警）：终端已经判断好了，开始即结束，不用按上升沿、下降沿合并
     */
    public Alarm instantAlarm(VehicleInfo v, Alarm.Source source, String type, String typeName, int level,
                              LocalDateTime time, double lat, double lng, double speed, int serial, String detail) {
        Alarm a = new Alarm(v.id(), v.plate(), v.fleetId(), v.deviceId(), source, type, typeName, level, time, lat, lng, speed);
        a.setEndTime(time);
        a.setSerialNo(serial);
        a.setDetail(detail);
        a = repo.save(a);
        log.info("{} {} 主动安全报警：{}", v.plate(), v.deviceId(), typeName);
        events.publishEvent(new AlarmEvent(a));
        return a;
    }

    private void hold(DeviceState s, VehicleInfo v, String type, String name, int level, Alarm.Source source,
                      LocalDateTime time, Location l, int serial, String detail) {
        Open open = s.open.get(type);
        if (open != null) {
            open.count++;
            open.maxSpeed = Math.max(open.maxSpeed, l.getSpeed());
            return;
        }
        Alarm a = new Alarm(v.id(), v.plate(), v.fleetId(), v.deviceId(), source, type, name, level, time,
                l.getLatitude(), l.getLongitude(), l.getSpeed());
        a.setSerialNo(serial);
        a.setDetail(detail);
        a = repo.save(a);
        s.open.put(type, new Open(a.getId(), l.getSpeed()));
        log.info("{} {} 开始报警：{}", v.plate(), v.deviceId(), name);
        events.publishEvent(new AlarmEvent(a));
    }

    private void release(DeviceState s, String type, LocalDateTime time) {
        Open open = s.open.remove(type);
        if (open != null) {
            end(open, time);
        }
    }

    private void closeAll(String deviceId, LocalDateTime time) {
        DeviceState s = states.remove(deviceId);
        if (s == null) {
            return;
        }
        for (Iterator<Open> it = s.open.values().iterator(); it.hasNext(); ) {
            end(it.next(), time);
            it.remove();
        }
    }

    private void end(Open open, LocalDateTime time) {
        repo.findById(open.alarmId).ifPresent(a -> {
            a.setEndTime(time.isBefore(a.getStartTime()) ? a.getStartTime() : time);
            a.setCount(open.count);
            a.setSpeed(Math.max(a.getSpeed(), open.maxSpeed));
            repo.save(a);
        });
    }

    /**
     * 有附加信息的报警，把附加信息里的细节写进报警说明
     */
    private static String detail(int bit, Location l) {
        Object v;
        switch (bit) {
            case 1:
                v = l.getExtras().get(AlarmExtras.Overspeed.ID);
                return v == null ? String.format("速度 %.1f km/h", l.getSpeed()) : String.valueOf(v);
            case 20:
            case 21:
                v = l.getExtras().get(AreaAlarmInfo.ID);
                return v == null ? null : String.valueOf(v);
            case 22:
                v = l.getExtras().get(AlarmExtras.SegmentTime.ID);
                return v == null ? null : String.valueOf(v);
            default:
                return null;
        }
    }

    /**
     * 当前没有结束的报警数，按车辆统计
     */
    public int openCount(String deviceId) {
        DeviceState s = states.get(deviceId);
        return s == null ? 0 : s.open.size();
    }

    private static final class DeviceState {
        final Map<String, Open> open = new HashMap<>();
        LocalDateTime drivingSince;
        LocalDateTime restSince;
    }

    private static final class Open {
        final long alarmId;
        int count = 1;
        double maxSpeed;

        Open(long alarmId, double speed) {
            this.alarmId = alarmId;
            this.maxSpeed = speed;
        }
    }

    public record AlarmEvent(Alarm alarm) {
    }
}
