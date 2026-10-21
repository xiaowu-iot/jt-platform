package io.github.xiaowuiot.jt.server.track;

import io.github.xiaowuiot.protocol.jt808.Jt808Flags;
import io.github.xiaowuiot.jt.server.common.ApiException;
import io.github.xiaowuiot.jt.server.config.Roles;
import io.github.xiaowuiot.jt.server.geo.Geo;
import io.github.xiaowuiot.jt.server.security.DataScope;
import io.github.xiaowuiot.jt.server.vehicle.VehicleCache;
import io.github.xiaowuiot.jt.server.vehicle.VehicleInfo;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 轨迹回放。
 * <pre>
 * GET /api/tracks/12?from=2026-10-20 08:00:00&to=2026-10-20 18:00:00&filterDrift=true
 * </pre>
 * 返回的点已经转成 GCJ-02，前端直接往高德底图上画；一次最多查 7 天。
 */
@RestController
@RequestMapping("/api/tracks")
@Roles.On(Roles.PLATFORM)
@PreAuthorize("hasAuthority('PERM_VEHICLE_VIEW')")
public class TrackController {

    private static final String PATTERN = "yyyy-MM-dd HH:mm:ss";

    private final TrackService tracks;
    private final VehicleCache vehicles;
    private final DataScope scope;

    public TrackController(TrackService tracks, VehicleCache vehicles, DataScope scope) {
        this.tracks = tracks;
        this.vehicles = vehicles;
        this.scope = scope;
    }

    /**
     * @param t 时间，lat/lng GCJ-02，s 速度，d 方向，m 里程，a 报警名称
     */
    public record Point(LocalDateTime t, double lat, double lng, double s, int d, Double m, boolean acc, List<String> a) {
    }

    public record Stop(LocalDateTime start, LocalDateTime end, long seconds, double lat, double lng, boolean accOn) {
    }

    public record Result(String plate, List<Point> points, List<Stop> stops, int rawCount, int driftCount,
                         double distanceKm, Double meterKm, double maxSpeed, long seconds) {
    }

    @GetMapping("/{vehicleId}")
    public Result playback(@PathVariable Long vehicleId,
                           @RequestParam @DateTimeFormat(pattern = PATTERN) LocalDateTime from,
                           @RequestParam @DateTimeFormat(pattern = PATTERN) LocalDateTime to,
                           @RequestParam(defaultValue = "true") boolean filterDrift) {
        VehicleInfo v = vehicles.byId(vehicleId);
        if (v == null) {
            throw ApiException.notFound("车辆不存在");
        }
        if (!scope.canSee(v.fleetId())) {
            throw ApiException.forbidden("没有权限查看这辆车");
        }
        if (!to.isAfter(from)) {
            throw ApiException.badRequest("结束时间要晚于开始时间");
        }
        if (Duration.between(from, to).toDays() > 7) {
            throw ApiException.badRequest("一次最多查 7 天的轨迹");
        }
        TrackService.Playback p = tracks.playback(vehicleId, from, to, filterDrift);
        List<Point> points = p.points().stream().map(t -> {
            double[] g = Geo.wgs84ToGcj02(t.lat(), t.lng());
            return new Point(t.gpsTime(), round6(g[0]), round6(g[1]), t.speed(), t.direction(), t.mileage(), t.accOn(),
                    t.alarm() == 0 ? List.of() : Jt808Flags.alarmNames(t.alarm()));
        }).toList();
        List<Stop> stops = p.stops().stream().map(s -> {
            double[] g = Geo.wgs84ToGcj02(s.lat(), s.lng());
            return new Stop(s.start(), s.end(), s.seconds(), round6(g[0]), round6(g[1]), s.accOn());
        }).toList();
        return new Result(v.plate(), points, stops, p.rawCount(), p.driftCount(), p.distanceKm(), p.meterKm(),
                p.maxSpeed(), p.seconds());
    }

    private static double round6(double v) {
        return Math.round(v * 1e6) / 1e6;
    }
}
