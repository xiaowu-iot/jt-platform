package io.github.xiaowuiot.jt.server.demo;

import io.github.xiaowuiot.protocol.jt808.Jt808Flags;
import io.github.xiaowuiot.jt.server.alarm.Alarm;
import io.github.xiaowuiot.jt.server.alarm.AlarmRepository;
import io.github.xiaowuiot.jt.server.alarm.AlarmTypes;
import io.github.xiaowuiot.jt.server.stat.DailyStat;
import io.github.xiaowuiot.jt.server.stat.DailyStatRepository;
import io.github.xiaowuiot.jt.server.vehicle.Vehicle;
import io.github.xiaowuiot.jt.server.vehicle.VehicleType;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * 演示数据：补上过去 7 天的每日统计和报警记录，首次打开时趋势图、报表、报警列表就有内容。
 * 今天的数据全部来自内置模拟终端的真实上报。
 */
@Component
public class DemoHistory {

    /** 历史报警的类型和权重：超速、疲劳最多，设备故障少 */
    private static final String[][] TYPES = {
            {"T1", "6"}, {AlarmTypes.PLATFORM_OVERSPEED, "8"}, {"T13", "5"}, {"T2", "3"}, {"T14", "4"},
            {"T19", "2"}, {"T5", "1"}, {"T7", "1"}, {"T29", "1"}, {"T25", "1"}, {AlarmTypes.PLATFORM_FATIGUE, "1"}};

    private static final String[] HANDLERS = {"王调度", "李监控"};
    private static final String[] REMARKS = {"已电话提醒司机减速", "已联系车队长核实", "司机已停车休息", "设备复位后恢复", "误报，已忽略"};

    private final DailyStatRepository stats;
    private final AlarmRepository alarms;

    public DemoHistory(DailyStatRepository stats, AlarmRepository alarms) {
        this.stats = stats;
        this.alarms = alarms;
    }

    public void generate(List<Vehicle> vehicles, Map<String, DemoRoutes.Route> routes) {
        Random r = new Random(20261020);
        LocalDate today = LocalDate.now();
        List<DailyStat> dayStats = new ArrayList<>();
        List<Alarm> alarmList = new ArrayList<>();
        int totalWeight = 0;
        for (String[] t : TYPES) {
            totalWeight += Integer.parseInt(t[1]);
        }
        for (int back = 7; back >= 1; back--) {
            LocalDate day = today.minusDays(back);
            // 周末出车少一点
            double factor = day.getDayOfWeek().getValue() >= 6 ? 0.75 : 1.0;
            for (Vehicle v : vehicles) {
                if (r.nextInt(100) < 4) {
                    // 偶尔有车当天没出车（检修、休息）
                    continue;
                }
                double km = switch (v.getType()) {
                    case SANITATION -> 45 + r.nextDouble() * 45;
                    case MUCK -> 120 + r.nextDouble() * 110;
                    case COLD_CHAIN -> 260 + r.nextDouble() * 160;
                    case LOGISTICS -> 300 + r.nextDouble() * 200;
                    default -> 80 + r.nextDouble() * 80;
                } * factor;
                double avg = v.getType() == VehicleType.SANITATION ? 16 : v.getType() == VehicleType.MUCK ? 38 : 58;
                DailyStat s = new DailyStat(v.getId(), v.getFleetId(), day);
                s.setGpsKm(Math.round(km * 10) / 10.0);
                s.setRunSeconds((long) (km / avg * 3600));
                s.setMaxSpeed(Math.round(avg * (1.5 + r.nextDouble() * 0.4)));
                s.setPoints((int) (s.getRunSeconds() / 10) + 600);
                LocalDateTime first = day.atTime(5 + r.nextInt(3), r.nextInt(60));
                s.setFirstTime(first);
                s.setLastTime(first.plusSeconds(s.getRunSeconds() + 3600L * (2 + r.nextInt(3))));
                int n = r.nextInt(100) < 55 ? r.nextInt(4) : 0;
                s.setAlarms(n);
                dayStats.add(s);

                DemoRoutes.Route route = v.getDemoRoute() == null ? null : routes.get(v.getDemoRoute().split("#")[0]);
                for (int i = 0; i < n; i++) {
                    String type = pick(r, totalWeight);
                    String name = type.startsWith("T") ? Jt808Flags.alarmName(Integer.parseInt(type.substring(1)))
                            : AlarmTypes.all().get(type);
                    int level = type.startsWith("T") ? AlarmTypes.terminalLevel(Integer.parseInt(type.substring(1))) : AlarmTypes.IMPORTANT;
                    double[] p = route == null ? new double[]{22.54, 114.05} : route.points().get(r.nextInt(route.points().size()));
                    LocalDateTime start = day.atTime(7 + r.nextInt(12), r.nextInt(60), r.nextInt(60));
                    double speed = type.contains("OVERSPEED") || "T1".equals(type) || "T13".equals(type)
                            ? avg * 1.6 + r.nextInt(12) : avg * (0.5 + r.nextDouble() * 0.6);
                    Alarm a = new Alarm(v.getId(), v.getPlate(), v.getFleetId(), v.getDeviceId(),
                            type.startsWith("T") ? Alarm.Source.TERMINAL : Alarm.Source.PLATFORM, type, name, level, start,
                            p[0], p[1], Math.round(speed * 10) / 10.0);
                    a.setEndTime(start.plusSeconds(30 + r.nextInt(300)));
                    a.setCount(1 + r.nextInt(12));
                    int ri = r.nextInt(REMARKS.length);
                    a.handle(ri == REMARKS.length - 1 ? Alarm.Status.IGNORED : Alarm.Status.HANDLED,
                            HANDLERS[r.nextInt(HANDLERS.length)], REMARKS[ri], start.plusMinutes(2 + r.nextInt(15)));
                    alarmList.add(a);
                }
            }
        }
        stats.saveAll(dayStats);
        alarmList.sort((a, b) -> a.getStartTime().compareTo(b.getStartTime()));
        alarms.saveAll(alarmList);
    }

    private static String pick(Random r, int total) {
        int x = r.nextInt(total);
        for (String[] t : TYPES) {
            x -= Integer.parseInt(t[1]);
            if (x < 0) {
                return t[0];
            }
        }
        return TYPES[0][0];
    }
}
