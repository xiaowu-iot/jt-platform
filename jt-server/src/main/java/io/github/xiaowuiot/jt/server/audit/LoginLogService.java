package io.github.xiaowuiot.jt.server.audit;

import io.github.xiaowuiot.jt.server.config.Roles;
import io.github.xiaowuiot.jt.server.system.User;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 写登录记录。记录失败只打日志，不影响登录；记录保留 jt.audit.retention-days 天（默认 180）。
 */
@Service
@Roles.On(Roles.PLATFORM)
public class LoginLogService {

    private static final Logger log = LoggerFactory.getLogger(LoginLogService.class);

    private final LoginLogRepository logins;
    private final int retentionDays;

    public LoginLogService(LoginLogRepository logins, @Value("${jt.audit.retention-days:180}") int retentionDays) {
        this.logins = logins;
        this.retentionDays = retentionDays;
    }

    /**
     * @param user     认出来的用户，用户名不存在时为 null
     * @param username 登录时填的用户名
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(User user, String username, LoginLog.Action action, String message, HttpServletRequest req) {
        try {
            logins.save(new LoginLog(user == null ? null : user.getId(), user == null ? username : user.getUsername(),
                    user == null ? null : user.getName(), action, message, RequestInfo.of(req)));
        } catch (RuntimeException e) {
            log.warn("写登录记录失败：{}", e.getMessage());
        }
    }

    @Scheduled(cron = "0 47 3 * * *")
    @Transactional
    public void cleanup() {
        if (retentionDays <= 0) {
            return;
        }
        int n = logins.deleteBefore(LocalDateTime.now().minusDays(retentionDays));
        if (n > 0) {
            log.info("清理了 {} 条 {} 天前的登录记录", n, retentionDays);
        }
    }
}
