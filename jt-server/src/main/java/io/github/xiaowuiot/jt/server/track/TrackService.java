package io.github.xiaowuiot.jt.server.track;

import io.github.xiaowuiot.protocol.jt808.MessageId;
import io.github.xiaowuiot.protocol.jt808.body.Location;
import io.github.xiaowuiot.protocol.jt808.body.LocationBatch;
import io.github.xiaowuiot.jt.server.bus.UplinkBus;
import io.github.xiaowuiot.jt.server.bus.UplinkMessage;
import io.github.xiaowuiot.jt.server.config.JtProperties;
import io.github.xiaowuiot.jt.server.config.Roles;
import io.github.xiaowuiot.jt.server.geo.Geo;
import io.github.xiaowuiot.jt.server.vehicle.VehicleCache;
import io.github.xiaowuiot.jt.server.vehicle.VehicleInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;

/**
 * 轨迹：从总线收位置，攒一批写库；回放时过滤漂移点、识别停留点。
 */
@Service
@Roles.On(Roles.PLATFORM)
public class TrackService {

    private static final Logger log = LoggerFactory.getLogger(TrackService.class);

    /**
     * 回放时两点之间算出来的速度超过这个值，就认为后一个点是漂移点（GPS 跳点）。货车、客车很少超过 120 km/h
     */
    private static final double DRIFT_SPEED_KMH = 160;

    /**
     * 速度小于 1 km/h 持续这么久算一次停留
     */
    private static final Duration STOP_MIN = Duration.ofMinutes(3);

    private static final int MAX_QUEUE = 200_000;

    private final TrackStore store;
    private final VehicleCache vehicles;
    private final JtProperties props;
    private final ConcurrentLinkedQueue<TrackPoint> queue = new ConcurrentLinkedQueue<>();
    private final AtomicInteger queued = new AtomicInteger();
    private final LongAdder written = new LongAdder();
    private final LongAdder dropped = new LongAdder();

    public TrackService(TrackStore store, VehicleCache vehicles, JtProperties props, UplinkBus bus) {
        this.store = store;
        this.vehicles = vehicles;
        this.props = props;
        bus.subscribe("track", this::onMessage);
    }

    void onMessage(UplinkMessage m) {
        if (m.kind() != UplinkMessage.Kind.MESSAGE) {
            return;
        }
        if (m.msgId() == MessageId.LOCATION) {
            add(m.deviceId(), m.receiveTime(), m.body(), false);
        } else if (m.msgId() == MessageId.LOCATION_BATCH) {
            LocationBatch b = m.body();
            b.getLocations().forEach(l -> add(m.deviceId(), m.receiveTime(), l, b.isBlindArea()));
        }
    }

    private void add(String deviceId, long receiveTime, Location l, boolean blind) {
        VehicleInfo v = vehicles.byDevice(deviceId);
        // 没有档案的终端、没定位的点（经纬度全 0）、没有时间的点都不存
        if (v == null || v.id() == null || l.getTime() == null || (l.getLatitude() == 0 && l.getLongitude() == 0)) {
            return;
        }
        if (queued.get() >= MAX_QUEUE) {
            // 数据库写不过来：宁可丢轨迹点，也不能把内存撑爆
            dropped.increment();
            return;
        }
        queue.add(new TrackPoint(v.id(), deviceId, l.getTime(),
                LocalDateTime.ofInstant(Instant.ofEpochMilli(receiveTime), ZoneId.systemDefault()),
                l.getLatitude(), l.getLongitude(), l.getSpeed(), l.getDirection(), l.getAltitude(), l.getMileageKm(),
                l.getAlarm(), l.getStatus(), blind));
        queued.incrementAndGet();
    }

    /**
     * 每秒写一次库
     */
    @Scheduled(fixedDelay = 1000)
    public void flush() {
        List<TrackPoint> batch = new ArrayList<>();
        TrackPoint p;
        while (batch.size() < 5000 && (p = queue.poll()) != null) {
            batch.add(p);
        }
        if (batch.isEmpty()) {
            return;
        }
        queued.addAndGet(-batch.size());
        try {
            store.insert(batch);
            written.add(batch.size());
        } catch (RuntimeException e) {
            dropped.add(batch.size());
            log.warn("写入轨迹失败，丢弃 {} 个点", batch.size(), e);
        }
    }

    /**
     * 每天凌晨 3 点删掉过期的轨迹表
     */
    @Scheduled(cron = "0 0 3 * * *")
    public void cleanup() {
        store.dropBefore(YearMonth.now().minusMonths(props.getTrack().getRetentionMonths() - 1L));
    }

    public long written() {
        return written.sum();
    }

    public long dropped() {
        return dropped.sum();
    }

    public int pending() {
        return queued.get();
    }

    /**
     * 轨迹回放：查出原始点，过滤漂移，识别停留，算里程
     */
    public Playback playback(long vehicleId, LocalDateTime from, LocalDateTime to, boolean filterDrift) {
        List<TrackPoint> raw = store.query(vehicleId, from, to, 50_000);
        List<TrackPoint> kept = new ArrayList<>(raw.size());
        // 和上一个保留点比“跳”得太远的点先放这里。连续 3 个点都落在同一片（彼此不跳），
        // 说明车真的到了那里（断线很久后重连、终端重启），不是漂移，整批收下
        List<TrackPoint> jumped = new ArrayList<>();
        for (TrackPoint p : raw) {
            if (filterDrift) {
                // 状态位 bit1 = 0 表示没定位，坐标是终端缓存的旧值或 0，不要
                if ((p.status() & 0x2) == 0) {
                    continue;
                }
                if (!kept.isEmpty() && isDrift(kept.get(kept.size() - 1), p)) {
                    if (!jumped.isEmpty() && isDrift(jumped.get(jumped.size() - 1), p)) {
                        jumped.clear();
                    }
                    jumped.add(p);
                    if (jumped.size() >= 3) {
                        kept.addAll(jumped);
                        jumped.clear();
                    }
                    continue;
                }
                jumped.clear();
            }
            kept.add(p);
        }
        int drift = raw.size() - kept.size();
        List<Stop> stops = stops(kept);
        double distanceKm = 0;
        double maxSpeed = 0;
        for (int i = 0; i < kept.size(); i++) {
            TrackPoint p = kept.get(i);
            maxSpeed = Math.max(maxSpeed, p.speed());
            if (i > 0) {
                TrackPoint q = kept.get(i - 1);
                distanceKm += Geo.distance(q.lat(), q.lng(), p.lat(), p.lng()) / 1000;
            }
        }
        // 终端上报了里程的，以里程表为准（GPS 点算出来的距离会因为采样间隔偏小）
        Double first = kept.stream().map(TrackPoint::mileage).filter(m -> m != null && m > 0).findFirst().orElse(null);
        Double last = null;
        for (int i = kept.size() - 1; i >= 0 && last == null; i--) {
            Double m = kept.get(i).mileage();
            if (m != null && m > 0) {
                last = m;
            }
        }
        Double meterKm = first != null && last != null && last >= first ? last - first : null;
        long seconds = kept.size() < 2 ? 0 : Duration.between(kept.get(0).gpsTime(), kept.get(kept.size() - 1).gpsTime()).getSeconds();
        return new Playback(kept, stops, raw.size(), drift, round(distanceKm), meterKm == null ? null : round(meterKm),
                round(maxSpeed), seconds);
    }

    private static boolean isDrift(TrackPoint prev, TrackPoint p) {
        long sec = Math.abs(Duration.between(prev.gpsTime(), p.gpsTime()).getSeconds());
        double meters = Geo.distance(prev.lat(), prev.lng(), p.lat(), p.lng());
        if (sec == 0) {
            return meters > 100;
        }
        return meters / sec * 3.6 > DRIFT_SPEED_KMH;
    }

    private static List<Stop> stops(List<TrackPoint> points) {
        List<Stop> stops = new ArrayList<>();
        int start = -1;
        for (int i = 0; i <= points.size(); i++) {
            boolean still = i < points.size() && points.get(i).speed() < 1;
            if (still && start < 0) {
                start = i;
            } else if (!still && start >= 0) {
                TrackPoint a = points.get(start);
                TrackPoint b = points.get(i - 1);
                Duration d = Duration.between(a.gpsTime(), b.gpsTime());
                if (d.compareTo(STOP_MIN) >= 0) {
                    stops.add(new Stop(a.gpsTime(), b.gpsTime(), d.getSeconds(), a.lat(), a.lng(), a.accOn()));
                }
                start = -1;
            }
        }
        return stops;
    }

    private static double round(double v) {
        return Math.round(v * 10) / 10.0;
    }

    public record Stop(LocalDateTime start, LocalDateTime end, long seconds, double lat, double lng, boolean accOn) {
    }

    public record Playback(List<TrackPoint> points, List<Stop> stops, int rawCount, int driftCount, double distanceKm,
                           Double meterKm, double maxSpeed, long seconds) {
    }
}
