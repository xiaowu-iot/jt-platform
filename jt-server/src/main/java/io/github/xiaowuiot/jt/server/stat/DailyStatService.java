package io.github.xiaowuiot.jt.server.stat;

import io.github.xiaowuiot.protocol.jt808.MessageId;
import io.github.xiaowuiot.protocol.jt808.body.Location;
import io.github.xiaowuiot.jt.server.alarm.AlarmService;
import io.github.xiaowuiot.jt.server.bus.UplinkBus;
import io.github.xiaowuiot.jt.server.bus.UplinkMessage;
import io.github.xiaowuiot.jt.server.config.Roles;
import io.github.xiaowuiot.jt.server.geo.Geo;
import io.github.xiaowuiot.jt.server.vehicle.VehicleCache;
import io.github.xiaowuiot.jt.server.vehicle.VehicleInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 每日统计：内存里累加当天的数据，每分钟写一次库。重启后从库里接着累加。
 */
@Service
@Roles.On(Roles.PLATFORM)
public class DailyStatService {

    private static final Logger log = LoggerFactory.getLogger(DailyStatService.class);

    /** 两个点之间超过这么久，中间的时间不算行驶时长（终端离线、休眠） */
    private static final long MAX_GAP_SECONDS = 300;

    private final DailyStatRepository repo;
    private final VehicleCache vehicles;
    private final Map<Long, DailyStat> today = new ConcurrentHashMap<>();
    private final Set<Long> dirty = ConcurrentHashMap.newKeySet();
    private volatile LocalDate day = LocalDate.now();

    public DailyStatService(DailyStatRepository repo, VehicleCache vehicles, UplinkBus bus) {
        this.repo = repo;
        this.vehicles = vehicles;
        repo.findByDay(day).forEach(s -> today.put(s.getVehicleId(), s));
        bus.subscribe("daily-stat", this::onMessage);
    }

    void onMessage(UplinkMessage m) {
        if (m.kind() != UplinkMessage.Kind.MESSAGE || m.msgId() != MessageId.LOCATION) {
            return;
        }
        Location l = m.body();
        VehicleInfo v = vehicles.byDevice(m.deviceId());
        if (v == null || v.id() == null || l.getTime() == null || (l.getLatitude() == 0 && l.getLongitude() == 0)) {
            return;
        }
        if (!l.getTime().toLocalDate().equals(day)) {
            // 只统计今天的点：盲区补传的历史点、时钟不准的终端都不算进今天
            return;
        }
        DailyStat s = today.computeIfAbsent(v.id(), id -> new DailyStat(id, v.fleetId(), day));
        Double meter = l.getMileageKm();
        if (meter != null && meter > 0) {
            if (s.getFirstMeter() <= 0) {
                s.setFirstMeter(meter);
            }
            s.setLastMeter(Math.max(s.getLastMeter(), meter));
        }
        if (s.getLastTime() != null && l.getTime().isAfter(s.getLastTime())) {
            long gap = Duration.between(s.getLastTime(), l.getTime()).getSeconds();
            if (gap <= MAX_GAP_SECONDS) {
                double km = Geo.distance(s.getLastLat(), s.getLastLng(), l.getLatitude(), l.getLongitude()) / 1000;
                // 一个间隔里跑出 200 km/h 以上的距离，是漂移点，不算
                if (km / gap * 3600 < 200) {
                    s.setGpsKm(s.getGpsKm() + km);
                }
                if (l.getSpeed() > 0) {
                    s.setRunSeconds(s.getRunSeconds() + gap);
                }
            }
        }
        if (s.getFirstTime() == null) {
            s.setFirstTime(l.getTime());
        }
        if (s.getLastTime() == null || l.getTime().isAfter(s.getLastTime())) {
            s.setLastTime(l.getTime());
            s.setLastLat(l.getLatitude());
            s.setLastLng(l.getLongitude());
        }
        s.setFleetId(v.fleetId());
        s.setMaxSpeed(Math.max(s.getMaxSpeed(), l.getSpeed()));
        s.setPoints(s.getPoints() + 1);
        dirty.add(v.id());
    }

    @EventListener
    public void onAlarm(AlarmService.AlarmEvent e) {
        Long vid = e.alarm().getVehicleId();
        DailyStat s = today.computeIfAbsent(vid, id -> new DailyStat(id, e.alarm().getFleetId(), day));
        s.setAlarms(s.getAlarms() + 1);
        dirty.add(vid);
    }

    @Scheduled(fixedDelay = 60_000, initialDelay = 60_000)
    public void flush() {
        List<DailyStat> batch = new ArrayList<>();
        for (Long id : new ArrayList<>(dirty)) {
            dirty.remove(id);
            DailyStat s = today.get(id);
            if (s != null) {
                batch.add(s);
            }
        }
        if (!batch.isEmpty()) {
            try {
                for (DailyStat s : batch) {
                    // save 返回的是另一个对象，内存里这份继续被总线线程累加，只把新分配的 ID 记回来
                    DailyStat saved = repo.save(s);
                    if (s.getId() == null) {
                        s.assignId(saved.getId());
                    }
                }
            } catch (RuntimeException e) {
                log.warn("保存每日统计失败", e);
            }
        }
        LocalDate now = LocalDate.now();
        if (!now.equals(day)) {
            // 跨天：昨天的已经写完，换一张新的
            day = now;
            today.clear();
        }
    }

    public Collection<DailyStat> today() {
        return today.values();
    }

    public DailyStat today(Long vehicleId) {
        return today.get(vehicleId);
    }

    public LocalDate day() {
        return day;
    }
}
