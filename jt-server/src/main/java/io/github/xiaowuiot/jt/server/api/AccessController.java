package io.github.xiaowuiot.jt.server.api;

import io.github.xiaowuiot.protocol.codec.session.Session;
import io.github.xiaowuiot.protocol.codec.session.SessionManager;
import io.github.xiaowuiot.protocol.codec.stats.CodecStats;
import io.github.xiaowuiot.protocol.codec.stats.IoStats;
import io.github.xiaowuiot.protocol.codec.stats.RawMessageRecorder;
import io.github.xiaowuiot.protocol.jt808.Jt808Header;
import io.github.xiaowuiot.protocol.jt808.server.Jt808CommandSender;
import io.github.xiaowuiot.protocol.jt808.server.Jt808Server;
import io.github.xiaowuiot.jt.server.bus.LocalUplinkBus;
import io.github.xiaowuiot.jt.server.bus.UplinkBus;
import io.github.xiaowuiot.jt.server.config.Roles;
import io.github.xiaowuiot.jt.server.view.RawFrameView;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 接入层的运维接口：连接统计、在线会话、原始报文。
 * <pre>
 * GET  /api/access/stats                       连接数、流量、解码丢弃统计
 * GET  /api/access/sessions                    在线会话
 * GET  /api/access/raw/13800138000?minutes=10  最近 10 分钟的原始报文
 * POST /api/access/raw/13800138000/trace?on=true 把这台终端的报文全部写进 jt.raw 日志
 * </pre>
 */
@PreAuthorize("hasAuthority('PERM_RAW_VIEW')")
@RestController
@RequestMapping("/api/access")
@Roles.On(Roles.ACCESS)
public class AccessController {

    private final Jt808Server server;
    private final SessionManager sessions;
    private final UplinkBus bus;

    public AccessController(Jt808Server server, SessionManager sessions, UplinkBus bus) {
        this.server = server;
        this.sessions = sessions;
        this.bus = bus;
    }

    @GetMapping("/stats")
    public Map<String, Object> stats() {
        IoStats io = server.getTcpServer().getIoStats();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("port", server.getPort());
        m.put("connections", io.connections());
        m.put("sessions", sessions.size());
        m.put("accepted", io.accepted());
        m.put("bytesIn", io.bytesIn());
        m.put("bytesOut", io.bytesOut());
        m.put("pendingCommands", server.getCommandSender().pendingCount());
        if (bus instanceof LocalUplinkBus local) {
            m.put("busPending", local.pending());
            m.put("busPublished", local.published());
            m.put("busRejected", local.rejected());
        }
        Map<String, Long> codec = new LinkedHashMap<>();
        CodecStats.all().forEach((k, v) -> codec.put(k, v.sum()));
        m.put("codec", codec);
        return m;
    }

    @GetMapping("/sessions")
    public List<Map<String, Object>> sessions() {
        return sessions.all().stream()
                .filter(s -> Jt808CommandSender.PROTOCOL.equals(s.getProtocol()))
                .sorted(Comparator.comparing(Session::getDeviceId))
                .map(AccessController::sessionView)
                .toList();
    }

    @GetMapping("/raw/{deviceId}")
    public List<RawFrameView> raw(@PathVariable String deviceId, @RequestParam(defaultValue = "10") int minutes) {
        long now = System.currentTimeMillis();
        return recorder().query(deviceId, now - minutes * 60_000L, now).stream().map(RawFrameView::of).toList();
    }

    @PostMapping("/raw/{deviceId}/trace")
    public Map<String, Object> trace(@PathVariable String deviceId, @RequestParam boolean on) {
        recorder().trace(deviceId, on);
        return Map.of("traced", recorder().traced());
    }

    private RawMessageRecorder recorder() {
        return server.getTcpServer().getRawRecorder();
    }

    private static Map<String, Object> sessionView(Session s) {
        Jt808Header h = s.getAttribute(Jt808CommandSender.HEADER_TEMPLATE);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("deviceId", s.getDeviceId());
        m.put("phone", h == null ? null : h.getPhone());
        m.put("version", h == null ? null : h.getVersion().year());
        m.put("remote", String.valueOf(s.getChannel().remoteAddress()));
        m.put("onlineTime", s.getCreateTime());
        m.put("lastActiveTime", s.getLastActiveTime());
        return m;
    }
}
