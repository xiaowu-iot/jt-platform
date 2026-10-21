package io.github.xiaowuiot.jt.server.stat;

import io.github.xiaowuiot.jt.server.alarm.Alarm;
import io.github.xiaowuiot.jt.server.alarm.AlarmRepository;
import io.github.xiaowuiot.jt.server.alarm.AlarmTypes;
import io.github.xiaowuiot.jt.server.config.Roles;
import io.github.xiaowuiot.jt.server.fleet.Fleet;
import io.github.xiaowuiot.jt.server.fleet.FleetRepository;
import io.github.xiaowuiot.jt.server.geo.Geo;
import io.github.xiaowuiot.jt.server.platform.DeviceState;
import io.github.xiaowuiot.jt.server.platform.DeviceStateService;
import io.github.xiaowuiot.jt.server.vehicle.VehicleCache;
import io.github.xiaowuiot.jt.server.vehicle.VehicleInfo;
import io.github.xiaowuiot.jt.server.vehicle.VehicleService;
import io.github.xiaowuiot.jt.server.vehicle.VehicleType;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 运行概览：首页和数据大屏用的统计数字。按数据权限过滤，scope 为 null 表示全部车辆。
 */
@Service
@Roles.On(Roles.PLATFORM)
public class OverviewService {

    private final VehicleCache vehicles;
    private final DeviceStateService states;
    private final DailyStatService daily;
    private final DailyStatRepository dailyRepo;
    private final AlarmRepository alarms;
    private final FleetRepository fleets;

    public OverviewService(VehicleCache vehicles, DeviceStateService states, DailyStatService daily,
                           DailyStatRepository dailyRepo, AlarmRepository alarms, FleetRepository fleets) {
        this.vehicles = vehicles;
        this.states = states;
        this.daily = daily;
        this.dailyRepo = dailyRepo;
        this.alarms = alarms;
        this.fleets = fleets;
    }

    private List<VehicleInfo> visible(Set<Long> scope) {
        return vehicles.all().stream()
                .filter(v -> v.id() != null && (scope == null || scope.contains(v.fleetId())))
                .toList();
    }

    public Map<String, Object> summary(Set<Long> scope) {
        List<VehicleInfo> list = visible(scope);
        Map<String, Integer> byState = new HashMap<>();
        for (VehicleInfo v : list) {
            DeviceState s = v.deviceId() == null ? null : states.get(v.deviceId());
            byState.merge(VehicleService.state(s), 1, Integer::sum);
        }
        int total = list.size();
        int offline = byState.getOrDefault("OFFLINE", 0) + byState.getOrDefault("NEVER", 0);
        int online = total - offline;
        Set<Long> ids = list.stream().map(VehicleInfo::id).collect(Collectors.toSet());
        double km = 0;
        long runSeconds = 0;
        int alarmsToday = 0;
        for (DailyStat d : daily.today()) {
            if (ids.contains(d.getVehicleId())) {
                km += d.mileageKm();
                runSeconds += d.getRunSeconds();
                alarmsToday += d.getAlarms();
            }
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("total", total);
        m.put("online", online);
        m.put("offline", offline);
        m.put("running", byState.getOrDefault("RUNNING", 0));
        m.put("stopped", byState.getOrDefault("STOPPED", 0));
        m.put("alarm", byState.getOrDefault("ALARM", 0));
        m.put("onlineRate", total == 0 ? 0 : Math.round(online * 1000.0 / total) / 10.0);
        m.put("todayKm", Math.round(km * 10) / 10.0);
        m.put("todayRunHours", Math.round(runSeconds / 360.0) / 10.0);
        m.put("todayAlarms", alarmsToday);
        m.put("pendingAlarms", alarms.countByStatus(Alarm.Status.PENDING));
        return m;
    }

    /**
     * 按车辆类型统计：总数、在线数
     */
    public List<Map<String, Object>> byType(Set<Long> scope) {
        Map<VehicleType, int[]> m = new LinkedHashMap<>();
        for (VehicleInfo v : visible(scope)) {
            int[] c = m.computeIfAbsent(v.type(), k -> new int[2]);
            c[0]++;
            DeviceState s = v.deviceId() == null ? null : states.get(v.deviceId());
            if (s != null && s.isOnline()) {
                c[1]++;
            }
        }
        List<Map<String, Object>> list = new ArrayList<>();
        m.forEach((t, c) -> list.add(Map.of("type", t.name(), "label", t.label(), "total", c[0], "online", c[1])));
        list.sort(Comparator.comparing(x -> -((Integer) x.get("total"))));
        return list;
    }

    /**
     * 按顶层车队统计：车辆数、在线数、今日里程
     */
    public List<Map<String, Object>> byFleet(Set<Long> scope) {
        List<Fleet> all = fleets.findAll();
        Map<Long, Long> parentOf = new HashMap<>();
        all.forEach(f -> parentOf.put(f.getId(), f.getParentId()));
        Map<Long, Long> rootOf = new HashMap<>();
        for (Fleet f : all) {
            Long id = f.getId();
            // 往上找到顶层车队；限制层数，防止数据里有环
            for (int i = 0; i < 20 && parentOf.get(id) != null && parentOf.containsKey(parentOf.get(id)); i++) {
                id = parentOf.get(id);
            }
            rootOf.put(f.getId(), id);
        }
        Map<Long, String> names = all.stream().collect(Collectors.toMap(Fleet::getId, Fleet::getName));
        Map<Long, double[]> agg = new LinkedHashMap<>();
        for (VehicleInfo v : visible(scope)) {
            Long root = v.fleetId() == null ? 0L : rootOf.getOrDefault(v.fleetId(), v.fleetId());
            double[] a = agg.computeIfAbsent(root, k -> new double[3]);
            a[0]++;
            DeviceState s = v.deviceId() == null ? null : states.get(v.deviceId());
            if (s != null && s.isOnline()) {
                a[1]++;
            }
            DailyStat d = daily.today(v.id());
            if (d != null) {
                a[2] += d.mileageKm();
            }
        }
        List<Map<String, Object>> list = new ArrayList<>();
        agg.forEach((id, a) -> list.add(Map.of("fleetId", id, "name", names.getOrDefault(id, "未分组"),
                "total", (int) a[0], "online", (int) a[1], "km", Math.round(a[2] * 10) / 10.0)));
        list.sort(Comparator.comparing(x -> -((Integer) x.get("total"))));
        return list;
    }

    /**
     * 今日里程排行
     */
    public List<Map<String, Object>> mileageRank(Set<Long> scope, int top) {
        Map<Long, VehicleInfo> byId = visible(scope).stream().collect(Collectors.toMap(VehicleInfo::id, v -> v));
        return daily.today().stream()
                .filter(d -> byId.containsKey(d.getVehicleId()))
                .sorted(Comparator.comparingDouble(DailyStat::mileageKm).reversed())
                .limit(top)
                .map(d -> {
                    VehicleInfo v = byId.get(d.getVehicleId());
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("vehicleId", v.id());
                    m.put("plate", v.plate());
                    m.put("type", v.type().label());
                    m.put("km", d.mileageKm());
                    m.put("maxSpeed", Math.round(d.getMaxSpeed()));
                    m.put("runHours", Math.round(d.getRunSeconds() / 360.0) / 10.0);
                    return m;
                }).toList();
    }

    /**
     * 最近 days 天每天的总里程和报警次数（含今天）
     */
    public List<Map<String, Object>> dailyTrend(Set<Long> scope, int days) {
        Set<Long> ids = visible(scope).stream().map(VehicleInfo::id).collect(Collectors.toSet());
        LocalDate today = daily.day();
        LocalDate from = today.minusDays(days - 1L);
        Map<LocalDate, double[]> m = new LinkedHashMap<>();
        for (LocalDate d = from; !d.isAfter(today); d = d.plusDays(1)) {
            m.put(d, new double[3]);
        }
        for (DailyStat s : dailyRepo.findByDayBetween(from, today.minusDays(1))) {
            if (ids.contains(s.getVehicleId()) && m.containsKey(s.getDay())) {
                double[] a = m.get(s.getDay());
                a[0] += s.mileageKm();
                a[1] += s.getAlarms();
                a[2]++;
            }
        }
        for (DailyStat s : daily.today()) {
            if (ids.contains(s.getVehicleId())) {
                double[] a = m.get(today);
                a[0] += s.mileageKm();
                a[1] += s.getAlarms();
                a[2]++;
            }
        }
        List<Map<String, Object>> list = new ArrayList<>();
        m.forEach((d, a) -> list.add(Map.of("day", d.toString(), "km", Math.round(a[0] * 10) / 10.0,
                "alarms", (int) a[1], "active", (int) a[2])));
        return list;
    }

    /**
     * 最近 24 小时每小时的报警数
     */
    public List<Map<String, Object>> alarmHourly() {
        return alarmHourly(null);
    }

    /** @param scope 只统计这些车队的报警，null 表示全部 */
    public List<Map<String, Object>> alarmHourly(Set<Long> scope) {
        LocalDateTime end = LocalDateTime.now().truncatedTo(ChronoUnit.HOURS).plusHours(1);
        LocalDateTime start = end.minusHours(24);
        int[] counts = new int[24];
        for (LocalDateTime t : scope == null ? alarms.startTimesAfter(start)
                : scope.isEmpty() ? List.<LocalDateTime>of() : alarms.startTimesAfter(start, scope)) {
            int idx = (int) ChronoUnit.HOURS.between(start, t);
            if (idx >= 0 && idx < 24) {
                counts[idx]++;
            }
        }
        List<Map<String, Object>> list = new ArrayList<>();
        for (int i = 0; i < 24; i++) {
            list.add(Map.of("hour", start.plusHours(i).getHour() + ":00", "count", counts[i]));
        }
        return list;
    }

    /**
     * 今天各类报警的数量、各级别的数量
     */
    public Map<String, Object> alarmDistribution() {
        return alarmDistribution(null);
    }

    /** @param scope 只统计这些车队的报警，null 表示全部 */
    public Map<String, Object> alarmDistribution(Set<Long> scope) {
        LocalDateTime from = LocalDate.now().atStartOfDay();
        List<Object[]> byType = scope == null ? alarms.countByType(from) : scope.isEmpty() ? List.of() : alarms.countByType(from, scope);
        List<Object[]> byLevel = scope == null ? alarms.countByLevel(from) : scope.isEmpty() ? List.of() : alarms.countByLevel(from, scope);
        List<Map<String, Object>> types = byType.stream().limit(8)
                .map(r -> Map.<String, Object>of("name", r[0], "count", r[1])).toList();
        Map<String, Long> levels = new LinkedHashMap<>();
        levels.put("紧急", 0L);
        levels.put("重要", 0L);
        levels.put("一般", 0L);
        for (Object[] r : byLevel) {
            levels.merge(AlarmTypes.levelName(((Number) r[0]).intValue()), ((Number) r[1]).longValue(), Long::sum);
        }
        return Map.of("types", types, "levels", levels);
    }

    /**
     * 大屏地图：全部车辆的位置（GCJ-02）和状态
     */
    public List<Map<String, Object>> positions(Set<Long> scope) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (VehicleInfo v : visible(scope)) {
            DeviceState s = v.deviceId() == null ? null : states.get(v.deviceId());
            if (s == null || s.getRawLocation() == null) {
                continue;
            }
            double[] g = Geo.wgs84ToGcj02(s.getRawLocation().getLatitude(), s.getRawLocation().getLongitude());
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", v.id());
            m.put("plate", v.plate());
            m.put("type", v.type().name());
            m.put("icon", v.icon());
            m.put("state", VehicleService.state(s));
            m.put("lat", Math.round(g[0] * 1e6) / 1e6);
            m.put("lng", Math.round(g[1] * 1e6) / 1e6);
            m.put("speed", s.getRawLocation().getSpeed());
            m.put("dir", s.getRawLocation().getDirection());
            list.add(m);
        }
        return list;
    }
}
