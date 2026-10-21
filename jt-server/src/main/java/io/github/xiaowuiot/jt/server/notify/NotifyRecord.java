package io.github.xiaowuiot.jt.server.notify;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "jt_notify_record")
public class NotifyRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long channelId;

    @Column(length = 64)
    private String channelName;

    private Long alarmId;

    @Column(length = 64)
    private String title;

    @Column(length = 2000)
    private String content;

    private boolean success;

    @Column(length = 500)
    private String error;

    private LocalDateTime createdAt = LocalDateTime.now();

    protected NotifyRecord() {
    }

    public NotifyRecord(Long channelId, String channelName, Long alarmId, String title, String content) {
        this.channelId = channelId;
        this.channelName = channelName;
        this.alarmId = alarmId;
        this.title = title;
        this.content = content;
    }

    public Long getId() {
        return id;
    }

    public Long getChannelId() {
        return channelId;
    }

    public String getChannelName() {
        return channelName;
    }

    public Long getAlarmId() {
        return alarmId;
    }

    public String getTitle() {
        return title;
    }

    public String getContent() {
        return content;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
