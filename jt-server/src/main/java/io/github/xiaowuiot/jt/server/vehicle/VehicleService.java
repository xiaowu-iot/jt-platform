package io.github.xiaowuiot.jt.server.vehicle;

import io.github.xiaowuiot.protocol.jt808.body.Location;
import io.github.xiaowuiot.jt.server.common.ApiException;
import io.github.xiaowuiot.jt.server.common.Mask;
import io.github.xiaowuiot.jt.server.config.Roles;
import io.github.xiaowuiot.jt.server.fleet.Fleet;
import io.github.xiaowuiot.jt.server.fleet.FleetRepository;
import io.github.xiaowuiot.jt.server.geo.Geo;
import io.github.xiaowuiot.jt.server.platform.DeviceState;
import io.github.xiaowuiot.jt.server.platform.DeviceStateService;
import io.github.xiaowuiot.jt.server.stat.DailyStat;
import io.github.xiaowuiot.jt.server.stat.DailyStatService;
import io.github.xiaowuiot.jt.server.view.LocationView;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 车辆档案的增删改（同步更新缓存），以及档案和实时状态合在一起的视图。
 */
@Service
@Roles.On(Roles.PLATFORM)
public class VehicleService {

    private final VehicleRepository repo;
    private final VehicleCache cache;
    private final FleetRepository fleets;
    private final DeviceStateService states;
    private final DailyStatService daily;

    private final ApplicationEventPublisher events;

    public VehicleService(VehicleRepository repo, VehicleCache cache, FleetRepository fleets,
                          DeviceStateService states, DailyStatService daily, ApplicationEventPublisher events) {
        this.events = events;
        this.repo = repo;
        this.cache = cache;
        this.fleets = fleets;
        this.states = states;
        this.daily = daily;
    }

    public record VehicleForm(String plate, Integer plateColor, String vin, VehicleType type, Long fleetId,
                              String deviceId, String simNo, Integer speedLimit, String driverName, String driverPhone,
                              String remark, Boolean enabled, String icon) {
    }

    /**
     * 车辆 + 实时状态。经纬度给了两套：lat/lng 是 GCJ-02，直接往高德地图上画；location 里是终端原始的 WGS84
     */
    public record VehicleView(Long id, String plate, int plateColor, VehicleType type, String typeLabel, Long fleetId,
                              String fleetName, String deviceId, String simNo, String terminalId, String makerId,
                              String terminalModel, int protocolVersion, int speedLimit, String driverName,
                              String driverPhone, String remark, boolean enabled, LocalDateTime registeredAt,
                              boolean online, String state, LocalDateTime lastActive, Double lat, Double lng,
                              double speed, int direction, LocalDateTime gpsTime, List<String> alarms,
                              double todayKm, LocationView location, String cardDriver, Collection<?> sensors,
                              String icon) {
    }

    /**
     * 实时地图用的精简视图，几千辆车一次全部返回
     */
    public record MapView(Long id, String plate, VehicleType type, Long fleetId, String state, Double lat, Double lng,
                          double speed, int direction, LocalDateTime gpsTime, boolean alarm, String icon) {
    }

    @Transactional
    public Vehicle create(VehicleForm f) {
        if (f.plate() == null || f.plate().isBlank()) {
            throw ApiException.badRequest("请填写车牌号");
        }
        String plate = f.plate().trim().toUpperCase();
        if (repo.existsByPlate(plate)) {
            throw ApiException.badRequest("车牌 " + plate + " 已经存在");
        }
        String deviceId = normalizeDevice(f.deviceId());
        if (deviceId != null && repo.existsByDeviceId(deviceId)) {
            throw ApiException.badRequest("终端号 " + deviceId + " 已经绑定了其他车辆");
        }
        Vehicle v = new Vehicle(plate, f.type() == null ? VehicleType.OTHER : f.type(), deviceId);
        apply(v, f);
        v = repo.save(v);
        cache.put(v);
        events.publishEvent(new VehicleEvents.DeviceChanged(v.getId(), v.getPlate(), null, v.getDeviceId(), v.getSimNo(), operator()));
        return v;
    }

    /** 当前登录用户，后台任务里是"系统" */
    private static String operator() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        return a == null || a.getName() == null ? "系统" : a.getName();
    }

    @Transactional
    public Vehicle update(Long id, VehicleForm f) {
        Vehicle v = repo.findById(id).orElseThrow(() -> ApiException.notFound("车辆不存在"));
        if (f.plate() != null && !f.plate().isBlank()) {
            String plate = f.plate().trim().toUpperCase();
            if (!plate.equals(v.getPlate()) && repo.existsByPlate(plate)) {
                throw ApiException.badRequest("车牌 " + plate + " 已经存在");
            }
            v.setPlate(plate);
        }
        String deviceId = normalizeDevice(f.deviceId());
        String oldDevice = v.getDeviceId();
        String oldSim = v.getSimNo();
        if (deviceId != null && !deviceId.equals(v.getDeviceId())) {
            if (repo.existsByDeviceId(deviceId)) {
                throw ApiException.badRequest("终端号 " + deviceId + " 已经绑定了其他车辆");
            }
            // 换了终端，原来的鉴权码作废
            v.setDeviceId(deviceId);
            v.setAuthCode(null);
        }
        if (f.type() != null) {
            v.setType(f.type());
        }
        apply(v, f);
        v = repo.save(v);
        cache.put(v);
        if (!Objects.equals(oldDevice, v.getDeviceId()) || !Objects.equals(oldSim, v.getSimNo())) {
            events.publishEvent(new VehicleEvents.DeviceChanged(v.getId(), v.getPlate(), oldDevice, v.getDeviceId(), v.getSimNo(), operator()));
        }
        return v;
    }

    private static void apply(Vehicle v, VehicleForm f) {
        if (f.plateColor() != null) {
            v.setPlateColor(f.plateColor());
        }
        v.setVin(blank(f.vin()));
        v.setFleetId(f.fleetId());
        v.setSimNo(blank(f.simNo()));
        v.setSpeedLimit(f.speedLimit() == null ? 0 : f.speedLimit());
        v.setDriverName(blank(f.driverName()));
        if (f.driverPhone() != null && !Mask.isMasked(f.driverPhone())) {
            v.setDriverPhone(blank(f.driverPhone()));
        }
        v.setRemark(blank(f.remark()));
        if (f.icon() != null) {
            v.setIcon(icon(f.icon()));
        }
        if (f.enabled() != null) {
            v.setEnabled(f.enabled());
        }
    }

    /**
     * 车标：空串表示跟着车型走
     */
    private static String icon(String s) {
        if (s.isBlank()) {
            return null;
        }
        try {
            return VehicleType.valueOf(s.trim()).name();
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("不支持的车标：" + s);
        }
    }

    @Transactional
    public void delete(Long id) {
        String plate = repo.findById(id).map(Vehicle::getPlate).orElse(null);
        repo.deleteById(id);
        cache.remove(id);
        events.publishEvent(new VehicleEvents.Deleted(id, plate, operator()));
    }

    /**
     * 终端号：去掉前导 0，和平台从消息头里取的设备号一致
     */
    static String normalizeDevice(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        String d = s.trim();
        if (!d.chars().allMatch(Character::isDigit) || d.length() > 20) {
            throw ApiException.badRequest("终端号只能是数字，最长 20 位");
        }
        int i = 0;
        while (i < d.length() - 1 && d.charAt(i) == '0') {
            i++;
        }
        return d.substring(i);
    }

    private static String blank(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    public List<VehicleView> views(List<Vehicle> list) {
        Map<Long, String> fleetNames = fleets.findAll().stream().collect(Collectors.toMap(Fleet::getId, Fleet::getName));
        return list.stream().map(v -> view(v, fleetNames)).toList();
    }

    public VehicleView view(Vehicle v, Map<Long, String> fleetNames) {
        DeviceState s = v.getDeviceId() == null ? null : states.get(v.getDeviceId());
        Location l = s == null ? null : s.getRawLocation();
        double[] gcj = l == null ? null : Geo.wgs84ToGcj02(l.getLatitude(), l.getLongitude());
        DailyStat d = daily.today(v.getId());
        LocationView lv = l == null ? null : LocationView.of(l);
        return new VehicleView(v.getId(), v.getPlate(), v.getPlateColor(), v.getType(), v.getType().label(), v.getFleetId(),
                fleetNames.get(v.getFleetId()), v.getDeviceId(), v.getSimNo(), v.getTerminalId(), v.getMakerId(),
                v.getTerminalModel(), v.getProtocolVersion(), v.effectiveSpeedLimit(), v.getDriverName(),
                Mask.phone(v.getDriverPhone()), v.getRemark(), v.isEnabled(), v.getRegisteredAt(),
                s != null && s.isOnline(), state(s), s == null || s.getLastActiveTime() == 0 ? null : time(s.getLastActiveTime()),
                gcj == null ? null : gcj[0], gcj == null ? null : gcj[1], l == null ? 0 : l.getSpeed(),
                l == null ? 0 : l.getDirection(), l == null ? null : l.getTime(),
                lv == null ? List.of() : lv.alarms(), d == null ? 0 : d.mileageKm(), lv,
                null,
                java.util.List.of(), v.getIcon());
    }

    /**
     * 地图上所有车辆，按数据权限过滤
     */
    public List<MapView> mapViews(Set<Long> fleetScope) {
        return cache.all().stream()
                .filter(v -> v.id() != null && (fleetScope == null || fleetScope.contains(v.fleetId())))
                .map(v -> {
                    DeviceState s = v.deviceId() == null ? null : states.get(v.deviceId());
                    Location l = s == null ? null : s.getRawLocation();
                    double[] gcj = l == null ? null : Geo.wgs84ToGcj02(l.getLatitude(), l.getLongitude());
                    return new MapView(v.id(), v.plate(), v.type(), v.fleetId(), state(s),
                            gcj == null ? null : gcj[0], gcj == null ? null : gcj[1], l == null ? 0 : l.getSpeed(),
                            l == null ? 0 : l.getDirection(), l == null ? null : l.getTime(), l != null && l.getAlarm() != 0, v.icon());
                })
                .sorted(Comparator.comparing(MapView::plate))
                .toList();
    }

    /**
     * OFFLINE 离线、ALARM 报警、RUNNING 行驶、STOPPED 停车、NEVER 从未上线
     */
    public static String state(DeviceState s) {
        if (s == null || s.getRawLocation() == null && !s.isOnline()) {
            return "NEVER";
        }
        if (!s.isOnline()) {
            return "OFFLINE";
        }
        Location l = s.getRawLocation();
        if (l == null) {
            return "STOPPED";
        }
        if (l.getAlarm() != 0) {
            return "ALARM";
        }
        return l.getSpeed() > 0 ? "RUNNING" : "STOPPED";
    }

    private static LocalDateTime time(long millis) {
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), ZoneId.systemDefault());
    }

    public Map<Long, String> fleetNames() {
        return fleets.findAll().stream().collect(Collectors.toMap(Fleet::getId, Fleet::getName));
    }

    public static <T> Map<Long, T> byId(List<T> list, Function<T, Long> id) {
        return list.stream().collect(Collectors.toMap(id, Function.identity()));
    }
}
