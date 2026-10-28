package io.github.xiaowuiot.jt.server.demo;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.xiaowuiot.protocol.jt808.Jt808Version;
import io.github.xiaowuiot.protocol.jt808.server.Jt808Server;
import io.github.xiaowuiot.jt.server.config.JtProperties;
import io.github.xiaowuiot.jt.server.geo.Geo;
import io.github.xiaowuiot.jt.server.track.TrackPoint;
import io.github.xiaowuiot.jt.server.track.TrackStore;
import io.github.xiaowuiot.jt.server.vehicle.Vehicle;
import io.github.xiaowuiot.jt.server.vehicle.VehicleRepository;
import io.github.xiaowuiot.jt.server.vehicle.VehicleType;
import io.github.xiaowuiot.jt.simulator.SimulatorStats;
import io.github.xiaowuiot.jt.simulator.jt808.Jt808Terminal;
import io.github.xiaowuiot.jt.simulator.jt808.Track;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import jakarta.annotation.PreDestroy;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/**
 * 内置演示车队：平台启动后，按演示路线给每辆演示车起一个模拟终端，连到本机的 808 接入端口。
 * <p>
 * 和真实终端走的是完全一样的链路：TCP 连接 → 注册鉴权 → 0x0200 → 总线 → 轨迹、报警、统计。
 * 没有真机也能看到车在真实道路上跑、报警、回放轨迹，接上真机后关掉 jt.demo.simulator 即可。
 */
@Component
public class DemoFleetSimulator {

    private static final Logger log = LoggerFactory.getLogger(DemoFleetSimulator.class);

    private final JtProperties props;
    private final VehicleRepository vehicles;
    private final ObjectProvider<Jt808Server> server;
    private final ObjectMapper json;
    private final ObjectProvider<TrackStore> trackStore;
    private final List<Jt808Terminal> terminals = new ArrayList<>();
    private EventLoopGroup group;

    public DemoFleetSimulator(JtProperties props, VehicleRepository vehicles, ObjectProvider<Jt808Server> server,
                              ObjectMapper json, ObjectProvider<TrackStore> trackStore) {
        this.props = props;
        this.vehicles = vehicles;
        this.server = server;
        this.json = json;
        this.trackStore = trackStore;
    }

    /**
     * 平台重启后，演示车从上次停下的地方接着开（找路线上离最后一个轨迹点最近的拐点），
     * 不然每次重启车都会瞬移回出发点，轨迹上多出一条直线。没有历史轨迹时返回 -1
     */
    private static int resumeIndex(TrackPoint last, List<double[]> route) {
        if (last == null) {
            return -1;
        }
        int best = -1;
        double min = Double.MAX_VALUE;
        for (int i = 0; i < route.size(); i++) {
            double d = Geo.distance(last.lat(), last.lng(), route.get(i)[0], route.get(i)[1]);
            if (d < min) {
                min = d;
                best = i;
            }
        }
        // 离路线太远（比如路线改过），就不接着开了
        return min < 2000 ? best : -1;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        Jt808Server s = server.getIfAvailable();
        if (!props.getDemo().isSimulator() || s == null) {
            return;
        }
        Map<String, DemoRoutes.Route> routes = DemoRoutes.load(json);
        List<Vehicle> demo = vehicles.findAll().stream().filter(v -> v.getDemoRoute() != null && v.getDeviceId() != null).toList();
        if (demo.isEmpty()) {
            return;
        }
        group = new NioEventLoopGroup(2);
        Bootstrap bootstrap = new Bootstrap().group(group).channel(NioSocketChannel.class)
                .option(ChannelOption.TCP_NODELAY, true)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5000);
        String host = "0.0.0.0".equals(props.getAccess().getBindAddress()) ? "127.0.0.1" : props.getAccess().getBindAddress();
        List<InetSocketAddress> remote = List.of(new InetSocketAddress(host, s.getPort()));
        SimulatorStats stats = new SimulatorStats();
        ThreadLocalRandom r = ThreadLocalRandom.current();
        int interval = Math.max(1, props.getDemo().getInterval());
        for (Vehicle v : demo) {
            String[] parts = v.getDemoRoute().split("#");
            DemoRoutes.Route route = routes.get(parts[0]);
            if (route == null) {
                continue;
            }
            int idx = parts.length > 1 ? Integer.parseInt(parts[1]) : 0;
            // 同一条路线上的几辆车均匀分布在不同位置
            TrackStore store = trackStore.getIfAvailable();
            TrackPoint last = store == null ? null : store.last(v.getId());
            int resume = resumeIndex(last, route.points());
            int start = resume >= 0 ? resume : idx * route.points().size() / 4 + r.nextInt(10);
            Track track = new Track(route.points().get(0)[0], route.points().get(0)[1]);
            track.followLoop(route.points(), start);
            // 里程表也接着上次的读数走
            track.setMileageKm(last != null && last.mileage() != null && last.mileage() > 0 ? last.mileage() : 20_000 + r.nextInt(130_000));
            speed(track, v.getType());
            Jt808Version version = v.getProtocolVersion() == 0 ? Jt808Version.V2013 : Jt808Version.ofYear(v.getProtocolVersion());
            Jt808Terminal t = new Jt808Terminal(v.getDeviceId(), version, bootstrap, remote, null, 60, interval,
                    stats, track, false);
            t.setRandomAlarms(true);
            if (v.getSimNo() != null) {
                t.setIccid(v.getSimNo());
            }
            if (v.getDriverName() != null) {
                t.setDriver(v.getDriverName(), String.format("4403%014d", v.getId() * 7919L));
                t.setDriverCardAfter(r.nextInt(10, 60));
            }
            terminals.add(t);
            // 错开上线时间，像真实车辆一样陆续上线
            group.schedule(t::connect, r.nextLong(200, 8000), TimeUnit.MILLISECONDS);
        }
        log.info("演示车队：启动 {} 台模拟终端，连接 {}，每 {} 秒上报一次位置", terminals.size(), remote.get(0), interval);
    }

    private static void speed(Track t, VehicleType type) {
        switch (type) {
            case SANITATION -> {
                t.setSpeedRange(6, 28);
                t.setStopChance(0.04);
            }
            case MUCK -> {
                t.setSpeedRange(20, 62);
                t.setStopChance(0.01);
            }
            case GARBAGE -> {
                t.setSpeedRange(15, 40);
                t.setStopChance(0.005);
            }
            case TRANSFER -> {
                t.setSpeedRange(25, 60);
                t.setStopChance(0.003);
            }
            case BUS, SCHOOL_BUS -> {
                t.setSpeedRange(22, 42);
                t.setStopChance(0.002);
            }
            case AMBULANCE -> t.setSpeedRange(35, 75);
            case COLD_CHAIN, LOGISTICS -> {
                t.setSpeedRange(35, 95);
                t.setStopChance(0.005);
            }
            default -> t.setSpeedRange(20, 80);
        }
    }

    @PreDestroy
    public void stop() {
        terminals.forEach(Jt808Terminal::stop);
        if (group != null) {
            group.shutdownGracefully(0, 2, TimeUnit.SECONDS);
        }
    }
}
