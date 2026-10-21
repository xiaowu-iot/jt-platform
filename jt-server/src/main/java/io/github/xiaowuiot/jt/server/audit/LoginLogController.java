package io.github.xiaowuiot.jt.server.audit;

import io.github.xiaowuiot.jt.server.common.PageResult;
import io.github.xiaowuiot.jt.server.config.Roles;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 登录记录：谁、什么时候、从哪个 IP、用什么浏览器登录或退出，登录失败也记。带着 IP，只有系统管理员能看。
 */
@RestController
@RequestMapping("/api/audit")
@Roles.On(Roles.PLATFORM)
@PreAuthorize("hasAuthority('PERM_SYSTEM_ADMIN')")
public class LoginLogController {

    private static final Map<LoginLog.Action, String> LABELS = Map.of(
            LoginLog.Action.LOGIN, "登录成功",
            LoginLog.Action.LOGIN_FAILED, "登录失败",
            LoginLog.Action.LOGOUT, "退出登录");

    private final LoginLogRepository logins;

    public LoginLogController(LoginLogRepository logins) {
        this.logins = logins;
    }

    public record LoginRow(Long id, Long userId, String username, String name, LoginLog.Action action, String actionLabel,
                           String message, String ip, String client, String browser, LocalDateTime createdAt) {
        static LoginRow of(LoginLog l) {
            return new LoginRow(l.getId(), l.getUserId(), l.getUsername(), l.getName(), l.getAction(), LABELS.get(l.getAction()),
                    l.getMessage(), l.getIp(), l.getClient(), browserOf(l.getUserAgent()), l.getCreatedAt());
        }
    }

    @GetMapping("/logins")
    public PageResult<LoginRow> logins(@RequestParam(required = false) String keyword,
                                       @RequestParam(required = false) LoginLog.Action action,
                                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                       @RequestParam(defaultValue = "0") int page,
                                       @RequestParam(defaultValue = "20") int size) {
        Specification<LoginLog> spec = (root, q, cb) -> {
            List<Predicate> ps = new ArrayList<>();
            if (keyword != null && !keyword.isBlank()) {
                String k = "%" + keyword.trim().toLowerCase() + "%";
                ps.add(cb.or(cb.like(cb.lower(root.get("username")), k), cb.like(cb.lower(root.get("name")), k),
                        cb.like(root.get("ip"), k)));
            }
            if (action != null) {
                ps.add(cb.equal(root.get("action"), action));
            }
            if (from != null) {
                ps.add(cb.greaterThanOrEqualTo(root.get("createdAt"), from.atStartOfDay()));
            }
            if (to != null) {
                ps.add(cb.lessThan(root.get("createdAt"), to.plusDays(1).atStartOfDay()));
            }
            return cb.and(ps.toArray(new Predicate[0]));
        };
        return PageResult.of(logins.findAll(spec, PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100),
                Sort.by(Sort.Direction.DESC, "id"))), LoginRow::of);
    }

    /** 从 User-Agent 粗略认出浏览器和系统，给页面显示用 */
    static String browserOf(String ua) {
        if (ua == null || ua.isBlank()) {
            return "";
        }
        String b = ua.contains("MicroMessenger") ? "微信" : ua.contains("DingTalk") ? "钉钉" : ua.contains("Edg/") ? "Edge"
                : ua.contains("Chrome/") ? "Chrome" : ua.contains("Firefox/") ? "Firefox" : ua.contains("Safari/") ? "Safari"
                : ua.contains("curl/") ? "curl" : "其他";
        String os = ua.contains("Windows") ? "Windows" : ua.contains("Android") ? "Android"
                : ua.contains("iPhone") || ua.contains("iPad") ? "iOS" : ua.contains("Mac OS") ? "macOS"
                : ua.contains("Linux") ? "Linux" : "";
        return os.isEmpty() ? b : b + " · " + os;
    }
}
