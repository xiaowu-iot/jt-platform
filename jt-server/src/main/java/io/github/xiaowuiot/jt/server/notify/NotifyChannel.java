package io.github.xiaowuiot.jt.server.notify;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 报警通知渠道：钉钉群机器人，或者推给自己系统的 Webhook。
 */
@Entity
@Table(name = "jt_notify_channel")
public class NotifyChannel {

    public enum Type { DINGTALK, WEBHOOK }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 64)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Type type = Type.DINGTALK;

    @Column(nullable = false, length = 500)
    private String url;

    /** 钉钉机器人"加签"的密钥 */
    @Column(length = 128)
    private String secret;

    /** 报警级别不低于这个才发：1 一般 2 重要 3 紧急 */
    private int minLevel = 2;

    private boolean enabled = true;

    protected NotifyChannel() {
    }

    public NotifyChannel(String name, Type type, String url, String secret, int minLevel) {
        this.name = name;
        this.type = type;
        this.url = url;
        this.secret = secret;
        this.minLevel = minLevel;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Type getType() {
        return type;
    }

    public void setType(Type type) {
        this.type = type;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }

    public int getMinLevel() {
        return minLevel;
    }

    public void setMinLevel(int minLevel) {
        this.minLevel = minLevel;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
