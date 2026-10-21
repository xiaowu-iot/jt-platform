package io.github.xiaowuiot.jt.server.vehicle;

import io.github.xiaowuiot.protocol.jt808.Jt808Header;
import io.github.xiaowuiot.protocol.jt808.Jt808Version;
import io.github.xiaowuiot.protocol.jt808.body.Auth;
import io.github.xiaowuiot.protocol.jt808.body.Register;
import io.github.xiaowuiot.protocol.jt808.body.RegisterResponse;
import io.github.xiaowuiot.protocol.jt808.server.Jt808AuthService;
import io.github.xiaowuiot.jt.server.config.JtProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 终端注册、鉴权按车辆档案校验，鉴权码存到档案里，平台重启后终端不用重新注册。
 * <p>
 * 在 IO 线程里调用：只读 {@link VehicleCache}，写库交给单独的线程，不阻塞 IO。
 * <p>
 * 档案里没有这台终端时：开了 {@code jt.access.auto-register}（演示、测试环境）就自动建一条档案；
 * 没开就回注册结果 4"数据库中无该终端"，终端会过一会儿再试。
 */
@Component
public class VehicleAuthService implements Jt808AuthService {

    private static final Logger log = LoggerFactory.getLogger(VehicleAuthService.class);

    private static final char[] CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();

    private final VehicleCache cache;
    private final VehicleRepository repo;
    private final boolean autoRegister;
    private final SecureRandom random = new SecureRandom();
    private final ExecutorService writer = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "vehicle-writer");
        t.setDaemon(true);
        return t;
    });

    private final ApplicationEventPublisher events;

    public VehicleAuthService(VehicleCache cache, VehicleRepository repo, JtProperties props, ApplicationEventPublisher events) {
        this.cache = cache;
        this.repo = repo;
        this.autoRegister = props.getAccess().isAutoRegister();
        this.events = events;
    }

    @Override
    public RegisterResult register(Jt808Header header, Register register) {
        String deviceId = header.deviceId();
        VehicleInfo info = cache.byDevice(deviceId);
        if (info == null && !autoRegister) {
            log.warn("终端 {} 没有登记，拒绝注册（车牌 {}）", deviceId, register.getPlate());
            // 记到设备台账的"待登记终端"里，管理员绑定到车辆后终端再注册就能通过
            int version = header.getVersion().year();
            writer.execute(() -> publish(new VehicleEvents.TerminalRegistered(deviceId, null, register.getTerminalId(),
                    register.getMakerId(), register.getModel(), register.getPlate(), version)));
            return RegisterResult.fail(RegisterResponse.TERMINAL_NOT_FOUND);
        }
        if (info != null && !info.enabled()) {
            return RegisterResult.fail(RegisterResponse.TERMINAL_NOT_FOUND);
        }
        // 同一台终端重复注册（比如它把鉴权码弄丢了），给它原来的鉴权码
        String code = info != null && info.authCode() != null ? info.authCode() : newCode();
        int version = header.getVersion().year();
        // 先更新缓存再异步写库：终端收到注册应答后马上就会鉴权，不能等库写完
        cache.updateAuth(deviceId, code, version);
        writer.execute(() -> saveRegister(deviceId, register, code, version));
        return RegisterResult.success(code);
    }

    private void saveRegister(String deviceId, Register r, String code, int version) {
        try {
            Vehicle v = repo.findByDeviceId(deviceId).orElse(null);
            if (v == null) {
                String plate = r.getPlate() == null || r.getPlate().isBlank() || repo.existsByPlate(r.getPlate().trim())
                        ? "未登记" + deviceId : r.getPlate().trim();
                v = new Vehicle(plate, VehicleType.OTHER, deviceId);
                v.setPlateColor(r.getPlateColor());
                v.setRemark("终端注册时自动创建");
                log.info("终端 {} 自动建档，车牌 {}", deviceId, plate);
            }
            v.setAuthCode(code);
            v.setTerminalId(r.getTerminalId());
            v.setMakerId(r.getMakerId());
            v.setTerminalModel(r.getModel());
            v.setProtocolVersion(version);
            v.setRegisteredAt(LocalDateTime.now());
            v = repo.save(v);
            cache.put(v);
            publish(new VehicleEvents.TerminalRegistered(deviceId, v.getId(), r.getTerminalId(), r.getMakerId(), r.getModel(),
                    v.getPlate(), version));
        } catch (RuntimeException e) {
            log.warn("保存终端 {} 的注册信息失败", deviceId, e);
        }
    }

    private void publish(Object event) {
        try {
            events.publishEvent(event);
        } catch (RuntimeException e) {
            log.warn("终端注册事件处理失败：{}", e.toString());
        }
    }

    @Override
    public boolean authenticate(Jt808Header header, Auth auth) {
        VehicleInfo info = cache.byDevice(header.deviceId());
        if (info == null || !info.enabled() || auth.getAuthCode() == null) {
            return false;
        }
        if (info.authCode() == null) {
            // 档案是手工录入的、终端从来没注册过：第一次鉴权直接认它带来的鉴权码（很多终端出厂就写好了鉴权码）
            String code = auth.getAuthCode();
            cache.updateAuth(header.deviceId(), code, info.protocolVersion());
            writer.execute(() -> repo.findByDeviceId(header.deviceId()).ifPresent(v -> {
                v.setAuthCode(code);
                cache.put(repo.save(v));
            }));
            return true;
        }
        return info.authCode().equals(auth.getAuthCode());
    }

    @Override
    public Jt808Version registeredVersion(String deviceId) {
        VehicleInfo info = cache.byDevice(deviceId);
        return info == null || info.protocolVersion() == 0 ? null : Jt808Version.ofYear(info.protocolVersion());
    }

    private String newCode() {
        char[] c = new char[12];
        for (int i = 0; i < c.length; i++) {
            c[i] = CHARS[random.nextInt(CHARS.length)];
        }
        return new String(c);
    }
}
