package io.github.xiaowuiot.jt.server.access;

import io.github.xiaowuiot.protocol.codec.session.Session;
import io.github.xiaowuiot.protocol.codec.session.SessionListener;
import io.github.xiaowuiot.protocol.codec.session.SessionManager;
import io.github.xiaowuiot.protocol.jt808.Jt808Message;
import io.github.xiaowuiot.protocol.jt808.body.Jt808Body;
import io.github.xiaowuiot.protocol.jt808.codec.Jt808Rsa;
import io.github.xiaowuiot.protocol.jt808.server.Jt808AuthService;
import io.github.xiaowuiot.protocol.jt808.server.Jt808Server;
import io.github.xiaowuiot.protocol.jt808.sensor.Sensors;
import io.github.xiaowuiot.jt.server.bus.UplinkBus;
import io.github.xiaowuiot.jt.server.bus.UplinkMessage;
import io.github.xiaowuiot.jt.server.command.CommandGateway;
import io.github.xiaowuiot.jt.server.config.JtProperties;
import io.github.xiaowuiot.jt.server.config.Roles;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.SmartLifecycle;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Paths;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * access 角色：808 终端接入。
 * <p>
 * 收到的业务消息统一交给 {@link UplinkBus}；总线收下了才给终端回应答。
 * 用 {@link SmartLifecycle} 控制启停：Spring 容器准备好之后才开始接收终端连接，停机时先断开终端，再销毁其他 Bean。
 */
@Configuration
@Roles.On(Roles.ACCESS)
public class AccessConfig {

    @Bean
    public SessionManager sessionManager() {
        return new SessionManager();
    }

    @Bean
    public Jt808Server jt808Server(JtProperties props, SessionManager sessions, Jt808AuthService authService,
                                   UplinkBus bus) {
        JtProperties.Access cfg = props.getAccess();
        // 传感器透传的解析在接入节点上做，集群部署时平台节点拿到的就是解析好的数据
        Sensors.register();
        sessions.addListener(new SessionListener() {
            @Override
            public void onOnline(Session session) {
                bus.publish(UplinkMessage.online(session.getDeviceId(), remote(session)));
            }

            @Override
            public void onOffline(Session session, boolean replaced) {
                // 被新连接挤掉的旧会话不算离线，终端其实还在线
                if (!replaced) {
                    bus.publish(UplinkMessage.offline(session.getDeviceId(), remote(session)));
                }
            }
        });
        Jt808Server server = Jt808Server.builder()
                .port(cfg.getPort())
                .bindAddress(cfg.getBindAddress())
                .idleSeconds(cfg.getIdleSeconds())
                .workerThreads(cfg.getWorkerThreads())
                .sessions(sessions)
                .authService(authService)
                .authTimeoutSeconds(cfg.getAuthTimeoutSeconds())
                .connectionLimits(cfg.getMaxConnections(), cfg.getMaxConnectionsPerIp())
                .rsa(cfg.isRsaEnabled() ? new Jt808Rsa(RsaKeyStore.load(Paths.get(props.getDataDir(), "jt808-rsa.key")),
                        cfg.isRsaEncryptDownlink()) : null)
                .listener((session, msg) -> bus.publish(UplinkMessage.of(session.getDeviceId(), msg, remote(session))))
                .build();
        server.getCommandSender().setUploadFlowControl(cfg.getUploadWindow(), cfg.getUploadAckTimeoutMillis());
        return server;
    }

    @Bean
    public SmartLifecycle jt808ServerLifecycle(Jt808Server server) {
        return new SmartLifecycle() {
            private volatile boolean running;

            @Override
            public void start() {
                try {
                    server.start();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(e);
                }
                running = true;
            }

            @Override
            public void stop() {
                server.stop();
                running = false;
            }

            @Override
            public boolean isRunning() {
                return running;
            }
        };
    }

    /**
     * 单机模式的指令下发：终端就连在本进程上
     */
    @Bean
    @ConditionalOnMissingBean(CommandGateway.class)
    public CommandGateway localCommandGateway(Jt808Server server, SessionManager sessions) {
        return new CommandGateway() {
            @Override
            public CompletableFuture<Jt808Message> send(String deviceId, int msgId, Object body, int timeoutSeconds) {
                if (body instanceof Jt808Body b) {
                    // 升级包、多边形围栏这类可能超过 1023 字节的，自动分包
                    return server.getCommandSender().sendLarge(deviceId, msgId, b, timeoutSeconds, TimeUnit.SECONDS);
                }
                return server.getCommandSender().send(deviceId, msgId, body, timeoutSeconds, TimeUnit.SECONDS);
            }

            @Override
            public boolean isOnline(String deviceId) {
                Session s = sessions.get(deviceId);
                return s != null && s.isActive();
            }
        };
    }

    private static String remote(Session session) {
        return String.valueOf(session.getChannel().remoteAddress());
    }
}
