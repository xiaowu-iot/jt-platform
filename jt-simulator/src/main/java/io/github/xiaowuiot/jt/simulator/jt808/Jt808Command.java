package io.github.xiaowuiot.jt.simulator.jt808;

import io.github.xiaowuiot.protocol.jt808.Jt808Version;
import io.github.xiaowuiot.jt.simulator.SimulatorStats;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.math.BigInteger;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/**
 * 模拟一批 808 终端。
 * <pre>
 * # 1 台终端，打印收发的每条消息
 * java -jar jt-simulator.jar jt808 --verbose
 * # 1000 台终端，每 10 秒上报一次位置，每秒建立 200 条连接
 * java -jar jt-simulator.jar jt808 --count 1000 --interval 10 --rate 200
 * # 压测：绑定多个本机 IP，突破单个 IP 只有约 6 万个可用端口的限制
 * java -jar jt-simulator.jar jt808 --count 50000 --local-ips 127.0.0.2,127.0.0.3,127.0.0.4
 * </pre>
 */
@Command(name = "jt808", mixinStandardHelpOptions = true, description = "模拟 JT/T 808 终端")
public class Jt808Command implements Callable<Integer> {

    private static final Logger log = LoggerFactory.getLogger(Jt808Command.class);

    @Option(names = "--host", defaultValue = "127.0.0.1", description = "平台地址，默认 ${DEFAULT-VALUE}")
    String host;

    @Option(names = "--port", defaultValue = "7611", description = "平台端口，默认 ${DEFAULT-VALUE}")
    int port;

    @Option(names = "--servers", split = ",", description = "多个平台地址 host:port，连接失败时依次切换，设置后忽略 --host 和 --port")
    List<String> servers;

    @Option(names = {"-n", "--count"}, defaultValue = "1", description = "终端数量，默认 ${DEFAULT-VALUE}")
    int count;

    @Option(names = "--start", defaultValue = "13800138000", description = "第一台终端的手机号，后面的依次加 1")
    String startPhone;

    @Option(names = "--version", defaultValue = "2013", description = "协议版本：2011、2013 或 2019，默认 ${DEFAULT-VALUE}")
    int version;

    @Option(names = "--interval", defaultValue = "10", description = "位置上报间隔（秒），默认 ${DEFAULT-VALUE}")
    int interval;

    @Option(names = "--heartbeat", defaultValue = "60", description = "心跳间隔（秒），默认 ${DEFAULT-VALUE}")
    int heartbeat;

    @Option(names = "--rate", defaultValue = "200", description = "每秒建立的连接数，默认 ${DEFAULT-VALUE}")
    int rate;

    @Option(names = "--threads", defaultValue = "0", description = "IO 线程数，0 表示 CPU 核数 × 2")
    int threads;

    @Option(names = "--local-ips", split = ",", description = "绑定的本机 IP，多个 IP 轮流使用")
    List<String> localIps;

    @Option(names = "--local-port-start", defaultValue = "0",
            description = "配合 --local-ips：显式指定本地端口，从这个端口开始分配。Windows 的临时端口池是全局共享的，绑多个 IP 也突破不了，要显式指定端口")
    int localPortStart;

    @Option(names = "--duration", defaultValue = "0", description = "运行多少秒后退出，0 表示一直运行")
    int duration;

    @Option(names = "--lat", defaultValue = "22.543096", description = "轨迹起点纬度")
    double lat;

    @Option(names = "--lng", defaultValue = "114.057865", description = "轨迹起点经度")
    double lng;

    @Option(names = "--mute-after", defaultValue = "0",
            description = "上线多少秒后停止发送任何数据、但不断开连接，模拟终端断电或进隧道，0 表示不启用")
    int muteAfter;

    @Option(names = "--follow-route", description = "收到设置路线 0x8606 后沿路线行驶")
    boolean followRoute;

    @Option(names = "--deviate-after", defaultValue = "0", description = "沿路线行驶多少秒后偏离路线")
    int deviateAfter;

    @Option(names = "--driver-card-after", defaultValue = "0", description = "上线多少秒后驾驶员插卡上班（0x0702）")
    int driverCardAfter;

    @Option(names = "--blind-after", defaultValue = "0", description = "上线多少秒后进入盲区（位置先缓存，出盲区后用 0x0704 补传）")
    int blindAfter;

    @Option(names = "--blind-seconds", defaultValue = "30", description = "盲区持续多少秒")
    int blindSeconds;

    @Option(names = "--emergency-after", defaultValue = "0", description = "上线多少秒后触发紧急报警，报警保持到平台确认")
    int emergencyAfter;

    @Option(names = "--random-alarms", description = "随机产生超速、疲劳、碰撞预警等报警，平台演示用")
    boolean randomAlarms;

    @Option(names = {"-v", "--verbose"}, description = "打印收到的每一条消息")
    boolean verbose;

    @Override
    public Integer call() throws Exception {
        EventLoopGroup group = new NioEventLoopGroup(threads);
        Bootstrap bootstrap = new Bootstrap()
                .group(group)
                .channel(NioSocketChannel.class)
                .option(ChannelOption.TCP_NODELAY, true)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 10_000)
                // 显式绑定本地端口时，断线重连要复用同一个端口
                .option(ChannelOption.SO_REUSEADDR, true)
                .option(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT);
        List<InetSocketAddress> remotes = new ArrayList<>();
        if (servers == null || servers.isEmpty()) {
            remotes.add(new InetSocketAddress(host, port));
        } else {
            for (String s : servers) {
                int colon = s.lastIndexOf(':');
                remotes.add(new InetSocketAddress(s.substring(0, colon), Integer.parseInt(s.substring(colon + 1))));
            }
        }
        Jt808Version jtVersion = Jt808Version.ofYear(version);
        SimulatorStats stats = new SimulatorStats();
        List<Jt808Terminal> terminals = new ArrayList<>(count);

        CountDownLatch done = new CountDownLatch(1);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            terminals.forEach(Jt808Terminal::stop);
            group.shutdownGracefully(0, 2, TimeUnit.SECONDS).syncUninterruptibly();
            done.countDown();
        }));

        log.info("开始模拟 {} 台 {} 版终端，连接 {}，每 {} 秒上报一次位置", count, version, remotes, interval);
        group.scheduleAtFixedRate(() -> log.info(stats.snapshot()), 10, 10, TimeUnit.SECONDS);

        BigInteger phone = new BigInteger(startPhone);
        long intervalNanos = TimeUnit.SECONDS.toNanos(1) / Math.max(1, rate);
        long next = System.nanoTime();
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int i = 0; i < count; i++) {
            InetSocketAddress local = null;
            if (localIps != null && !localIps.isEmpty()) {
                int port = localPortStart > 0 ? localPortStart + i / localIps.size() : 0;
                if (port > 65535) {
                    throw new IllegalArgumentException("本地端口不够用，增加 --local-ips 的数量");
                }
                local = new InetSocketAddress(localIps.get(i % localIps.size()), port);
            }
            // 每台车的起点在 10 公里范围内随机分布
            Track track = new Track(lat + random.nextDouble(-0.05, 0.05), lng + random.nextDouble(-0.05, 0.05));
            Jt808Terminal t = new Jt808Terminal(phone.add(BigInteger.valueOf(i)).toString(), jtVersion,
                    bootstrap, remotes, local, heartbeat, interval, stats, track, verbose);
            t.setMuteAfter(muteAfter);
            t.setEmergencyAfter(emergencyAfter);
            t.setRandomAlarms(randomAlarms);
            t.setRouteOptions(followRoute, deviateAfter);
            t.setDriverCardAfter(driverCardAfter);
            t.setBlindArea(blindAfter, blindSeconds);
            terminals.add(t);
            t.connect();
            // 控制建连速度：一下子发起几万个连接，平台的 accept 队列会被打满
            next += intervalNanos;
            long sleep = next - System.nanoTime();
            if (sleep > 0) {
                TimeUnit.NANOSECONDS.sleep(sleep);
            }
        }
        log.info("{} 台终端已全部发起连接", count);

        if (duration > 0) {
            if (!done.await(duration, TimeUnit.SECONDS)) {
                log.info(stats.total());
                log.info("连接失败原因：" + stats.failureSummary());
                System.exit(0);
            }
        } else {
            done.await();
        }
        return 0;
    }
}
