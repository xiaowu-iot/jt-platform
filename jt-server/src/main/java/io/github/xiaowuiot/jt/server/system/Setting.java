package io.github.xiaowuiot.jt.server.system;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * 页面上改的平台设置：一个键一段 JSON（到点判定规则、AI 模型、语音等）。没改过的键用配置文件里的值
 */
@Entity
@Table(name = "sys_setting")
public class Setting {

    @Id
    @Column(name = "setting_key", length = 64)
    private String key;

    @Column(name = "setting_value", length = 20000)
    private String value;

    private LocalDateTime updatedAt;

    @Column(length = 40)
    private String updatedBy;

    protected Setting() {
    }

    public Setting(String key) {
        this.key = key;
    }

    public String getKey() {
        return key;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public String getUpdatedBy() {
        return updatedBy;
    }

    public void setUpdatedBy(String updatedBy) {
        this.updatedBy = updatedBy;
    }
}
