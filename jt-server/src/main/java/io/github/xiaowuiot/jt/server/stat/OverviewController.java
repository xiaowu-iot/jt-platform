package io.github.xiaowuiot.jt.server.stat;

import io.github.xiaowuiot.protocol.jt808.server.Jt808Server;
import io.github.xiaowuiot.jt.server.alarm.AlarmRepository;
import io.github.xiaowuiot.jt.server.bus.LocalUplinkBus;
import io.github.xiaowuiot.jt.server.bus.UplinkBus;
import io.github.xiaowuiot.jt.server.config.JtProperties;
import io.github.xiaowuiot.jt.server.config.Roles;
import io.github.xiaowuiot.jt.server.geo.Geo;
import io.github.xiaowuiot.jt.server.security.DataScope;
import io.github.xiaowuiot.jt.server.track.TrackService;
import io.github.xiaowuiot.jt.server.vehicle.VehicleCache;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * 首页概览（按登录用户的数据权限）和数据大屏（全部车辆，不用登录）。
 * <p>
 * 大屏一般挂在指挥中心的电视上，不方便登录，所以接口放行；只给统计数字、车牌和位置，不含司机电话这类个人信息。
 * 不想公开的，把 jt.screen.public 设成 false。
 */
@RestController
@Roles.On(Roles.PLATFORM)
public class OverviewController {

    private final OverviewService overview;
    private final DataScope scope;
    private final AlarmRepository alarms;
    private final VehicleCache vehicles;
    private final TrackService tracks;
    private final ObjectProvider<Jt808Server> server;
    private final UplinkBus bus;
    private final JtProperties props;

    public OverviewController(OverviewService overview, DataScope scope, AlarmRepository alarms, VehicleCache vehicles,
                              TrackService tracks, ObjectProvider<Jt808Server> server, UplinkBus bus, JtProperties props) {
        this.overview = overview;
        this.scope = scope;
        this.alarms = alarms;
        this.vehicles = vehicles;
        this.tracks = tracks;
        this.server = server;
        this.bus = bus;
        this.props = props;
    }

    @GetMapping("/api/overview")
    @PreAuthorize("hasAuthority('PERM_VEHICLE_VIEW')")
    public Map<String, Object> dashboard() {
        var s = scope.fleetIds();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("summary", overview.summary(s));
        m.put("byType", overview.byType(s));
        m.put("byFleet", overview.byFleet(s));
        m.put("trend", overview.dailyTrend(s, 7));
        m.put("rank", overview.mileageRank(s, 10));
        m.put("alarmHourly", overview.alarmHourly());
        m.put("alarmDist", overview.alarmDistribution());
        m.put("access", access());
        return m;
    }

    /**
     * 接入层的运行情况：连接数、消息量、协议版本分布
     */
    private Map<String, Object> access() {
        Map<String, Object> m = new LinkedHashMap<>();
        Jt808Server s = server.getIfAvailable();
        if (s != null) {
            m.put("connections", s.getTcpServer().getIoStats().connections());
            m.put("bytesIn", s.getTcpServer().getIoStats().bytesIn());
        }
        if (bus instanceof LocalUplinkBus local) {
            m.put("messages", local.published());
        }
        m.put("trackPoints", tracks.written());
        Map<String, Integer> versions = new TreeMap<>();
        vehicles.all().forEach(v -> {
            if (v.protocolVersion() > 0) {
                versions.merge("JT/T 808-" + v.protocolVersion(), 1, Integer::sum);
            }
        });
        m.put("versions", versions);
        return m;
    }
}
