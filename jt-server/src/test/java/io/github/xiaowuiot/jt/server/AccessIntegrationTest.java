package io.github.xiaowuiot.jt.server;

import io.github.xiaowuiot.protocol.jt808.Jt808Message;
import io.github.xiaowuiot.protocol.jt808.Jt808Version;
import io.github.xiaowuiot.protocol.jt808.MessageId;
import io.github.xiaowuiot.protocol.jt808.body.TerminalParams;
import io.github.xiaowuiot.protocol.jt808.body.TextMessage;
import io.github.xiaowuiot.protocol.jt808.body.GeneralResponse;
import io.github.xiaowuiot.jt.server.alarm.Alarm;
import io.github.xiaowuiot.jt.server.alarm.AlarmRepository;
import io.github.xiaowuiot.jt.server.command.CommandGateway;
import io.github.xiaowuiot.jt.server.vehicle.VehicleCache;
import io.github.xiaowuiot.jt.server.platform.DeviceState;
import io.github.xiaowuiot.jt.server.platform.DeviceStateService;
import io.github.xiaowuiot.jt.simulator.SimulatorStats;
import io.github.xiaowuiot.jt.simulator.jt808.Jt808Terminal;
import io.github.xiaowuiot.jt.simulator.jt808.Track;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 起一个完整的服务端，用模拟器的终端连上来：注册、鉴权、上报位置、接收指令。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {"jt.access.port=17611", "spring.config.import=",
                "spring.jpa.properties.hibernate.hbm2ddl.halt_on_error=true", "spring.datasource.url=jdbc:h2:mem:access-test",
                "jt.demo.enabled=false", "jt.demo.simulator=false", "jt.data-dir=target/test-data"})
class AccessIntegrationTest {

    private static final int PORT = 17611;

    @Autowired
    DeviceStateService states;

    @Autowired
    CommandGateway gateway;

    @Autowired
    VehicleCache vehicleCache;

    @Autowired
    AlarmRepository alarms;

    private final EventLoopGroup group = new NioEventLoopGroup(1);
    private final List<Jt808Terminal> terminals = new ArrayList<>();

    @AfterEach
    void tearDown() {
        terminals.forEach(Jt808Terminal::stop);
        group.shutdownGracefully();
    }

    @Test
    void terminalsOfAllVersionsGoOnlineAndReceiveCommands() throws Exception {
        start("13700000001", Jt808Version.V2011);
        start("13800000001", Jt808Version.V2013);
        start("13900000001", Jt808Version.V2019);

        for (String id : List.of("13700000001", "13800000001", "13900000001")) {
            waitFor(() -> {
                DeviceState s = states.get(id);
                return s != null && s.isOnline() && s.getRawLocation() != null;
            });
            Jt808Message reply = gateway.send(id, MessageId.QUERY_PARAMS, null, 5).get();
            TerminalParams p = reply.getBody();
            assertEquals(1L, ((Number) p.getParams().get(TerminalParams.DEFAULT_REPORT_INTERVAL)).longValue());

            Jt808Message ack = gateway.send(id, MessageId.TEXT_MESSAGE,
                    new TextMessage(TextMessage.FLAG_DISPLAY, "前方路段施工，请减速慢行"), 5).get();
            assertTrue(ack.<GeneralResponse>getBody().isSuccess());
        }
    }

    /**
     * 紧急报警：终端按下报警按钮 → 平台记一条终端报警（自动建档的车）
     */
    @Test
    void emergencyAlarmIsRecorded() throws Exception {
        Jt808Terminal t = new Jt808Terminal("13600000110", Jt808Version.V2019, new Bootstrap().group(group).channel(NioSocketChannel.class),
                List.of(new InetSocketAddress("127.0.0.1", PORT)), null, 30, 1, new SimulatorStats(), new Track(22.54, 114.05), false);
        t.setEmergencyAfter(1);
        terminals.add(t);
        t.connect();
        waitFor(() -> alarms.findAll().stream().anyMatch(a -> "13600000110".equals(a.getDeviceId())
                && a.getSource() == Alarm.Source.TERMINAL));
    }

    private Jt808Terminal start(String phone, Jt808Version version) {
        Bootstrap bootstrap = new Bootstrap().group(group).channel(NioSocketChannel.class);
        Jt808Terminal t = new Jt808Terminal(phone, version, bootstrap,
                List.of(new InetSocketAddress("127.0.0.1", PORT)), null, 30, 1,
                new SimulatorStats(), new Track(22.54, 114.05), false);
        terminals.add(t);
        t.connect();
        return t;
    }

    private static void waitFor(BooleanSupplier condition) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 10_000;
        while (!condition.getAsBoolean()) {
            if (System.currentTimeMillis() > deadline) {
                throw new AssertionError("等待超时");
            }
            Thread.sleep(50);
        }
    }
}
