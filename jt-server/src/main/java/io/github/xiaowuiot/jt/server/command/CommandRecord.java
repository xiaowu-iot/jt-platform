package io.github.xiaowuiot.jt.server.command;

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
 * 一条下发记录：谁、什么时候、给哪台车发了什么，终端怎么应答的。
 */
@Entity
@Table(name = "jt_command", indexes = @Index(name = "idx_command_vehicle", columnList = "vehicleId,id"))
public class CommandRecord {

    public enum Status { SENDING, SUCCESS, FAILED, TIMEOUT, OFFLINE }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long vehicleId;

    @Column(length = 16)
    private String plate;

    @Column(length = 20)
    private String deviceId;

    private int msgId;

    @Column(length = 64)
    private String name;

    @Column(length = 1000)
    private String params;

    @Column(length = 32)
    private String operator;

    @Enumerated(EnumType.STRING)
    @Column(length = 16)
    private Status status = Status.SENDING;

    @Column(length = 2000)
    private String result;

    private long costMillis;

    private LocalDateTime createdAt = LocalDateTime.now();

    protected CommandRecord() {
    }

    public CommandRecord(Long vehicleId, String plate, String deviceId, int msgId, String name, String params, String operator) {
        this.vehicleId = vehicleId;
        this.plate = plate;
        this.deviceId = deviceId;
        this.msgId = msgId;
        this.name = name;
        this.params = params;
        this.operator = operator;
    }

    public void finish(Status status, String result, long costMillis) {
        this.status = status;
        this.result = result != null && result.length() > 2000 ? result.substring(0, 2000) : result;
        this.costMillis = costMillis;
    }

    public Long getId() {
        return id;
    }

    public Long getVehicleId() {
        return vehicleId;
    }

    public String getPlate() {
        return plate;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public int getMsgId() {
        return msgId;
    }

    public String getName() {
        return name;
    }

    public String getParams() {
        return params;
    }

    public String getOperator() {
        return operator;
    }

    public Status getStatus() {
        return status;
    }

    public String getResult() {
        return result;
    }

    public long getCostMillis() {
        return costMillis;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
