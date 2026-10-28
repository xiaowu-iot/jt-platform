package io.github.xiaowuiot.jt.server.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * 登录记录：登录成功、登录失败、退出登录。
 */
@Entity
@Table(name = "jt_login_log", indexes = {
        @Index(name = "idx_login_log_user", columnList = "userId, createdAt"),
        @Index(name = "idx_login_log_time", columnList = "createdAt")})
public class LoginLog {

    public enum Action {
        LOGIN, LOGIN_FAILED, LOGOUT
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId;

    @Column(length = 64, nullable = false)
    private String username;

    @Column(length = 64)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(length = 16, nullable = false)
    private Action action;

    @Column(length = 200)
    private String message;

    @Column(length = 64)
    private String ip;

    /** 管理后台、手机端、数据大屏 */
    @Column(length = 16)
    private String client;

    @Column(length = 255)
    private String userAgent;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    protected LoginLog() {
    }

    public LoginLog(Long userId, String username, String name, Action action, String message, RequestInfo req) {
        this.userId = userId;
        this.username = RequestInfo.cut(username == null || username.isBlank() ? "(空)" : username, 64);
        this.name = name;
        this.action = action;
        this.message = RequestInfo.cut(message, 200);
        this.ip = req.ip();
        this.client = req.client();
        this.userAgent = req.userAgent();
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public String getUsername() {
        return username;
    }

    public String getName() {
        return name;
    }

    public Action getAction() {
        return action;
    }

    public String getMessage() {
        return message;
    }

    public String getIp() {
        return ip;
    }

    public String getClient() {
        return client;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
