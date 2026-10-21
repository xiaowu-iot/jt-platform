package io.github.xiaowuiot.jt.server.alarm;

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
 * 一次报警：从报警位置 1 开始，到清零结束。
 * <p>
 * 终端在报警期间每次上报位置都带着报警位，不能每条位置都算一次报警，否则一次超速能刷出几十条。
 * 所以按"上升沿开始、下降沿结束"合并成一条，期间上报的次数记在 count 里。
 */
@Entity
@Table(name = "jt_alarm", indexes = {
        @Index(name = "idx_alarm_time", columnList = "startTime"),
        @Index(name = "idx_alarm_vehicle", columnList = "vehicleId,startTime")})
public class Alarm {

    public enum Status { PENDING, HANDLED, IGNORED }

    public enum Source { TERMINAL, PLATFORM, ADAS }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long vehicleId;

    @Column(length = 16)
    private String plate;

    private Long fleetId;

    @Column(length = 20)
    private String deviceId;

    @Enumerated(EnumType.STRING)
    @Column(length = 16)
    private Source source;

    /**
     * 报警类型：终端报警位是 T + 位号，比如 T0 紧急报警；平台判断的是 P_ 开头，比如 P_OVERSPEED
     */
    @Column(length = 32)
    private String type;

    @Column(length = 64)
    private String typeName;

    /** 1 一般 2 重要 3 紧急 */
    private int level;

    private LocalDateTime startTime;
    private LocalDateTime endTime;

    private double lat;
    private double lng;
    private double speed;

    /** 报警期间收到几次带这个报警位的位置 */
    private int count = 1;

    /** 触发报警的那条 0x0200 的流水号，人工确认报警 0x8203 要带上 */
    private int serialNo;

    @Column(length = 500)
    private String detail;

    @Enumerated(EnumType.STRING)
    @Column(length = 16)
    private Status status = Status.PENDING;

    @Column(length = 32)
    private String handlerName;

    @Column(length = 500)
    private String handleRemark;

    private LocalDateTime handleTime;

    /** 已经给终端下发过 0x8203 人工确认 */
    private boolean confirmed;

    protected Alarm() {
    }

    public Alarm(Long vehicleId, String plate, Long fleetId, String deviceId, Source source, String type, String typeName,
                 int level, LocalDateTime startTime, double lat, double lng, double speed) {
        this.vehicleId = vehicleId;
        this.plate = plate;
        this.fleetId = fleetId;
        this.deviceId = deviceId;
        this.source = source;
        this.type = type;
        this.typeName = typeName;
        this.level = level;
        this.startTime = startTime;
        this.lat = lat;
        this.lng = lng;
        this.speed = speed;
    }

    public void handle(Status status, String handler, String remark) {
        handle(status, handler, remark, LocalDateTime.now());
    }

    public void handle(Status status, String handler, String remark, LocalDateTime time) {
        this.status = status;
        this.handlerName = handler;
        this.handleRemark = remark;
        this.handleTime = time;
    }

    /**
     * 改回待处理（处置有误时）
     */
    public void reopen() {
        this.status = Status.PENDING;
        this.handlerName = null;
        this.handleRemark = null;
        this.handleTime = null;
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

    public Long getFleetId() {
        return fleetId;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public Source getSource() {
        return source;
    }

    public String getType() {
        return type;
    }

    public String getTypeName() {
        return typeName;
    }

    public int getLevel() {
        return level;
    }

    public LocalDateTime getStartTime() {
        return startTime;
    }

    public LocalDateTime getEndTime() {
        return endTime;
    }

    public void setEndTime(LocalDateTime endTime) {
        this.endTime = endTime;
    }

    public double getLat() {
        return lat;
    }

    public double getLng() {
        return lng;
    }

    public double getSpeed() {
        return speed;
    }

    public void setSpeed(double speed) {
        this.speed = speed;
    }

    public int getCount() {
        return count;
    }

    public void setCount(int count) {
        this.count = count;
    }

    public int getSerialNo() {
        return serialNo;
    }

    public void setSerialNo(int serialNo) {
        this.serialNo = serialNo;
    }

    public String getDetail() {
        return detail;
    }

    public void setDetail(String detail) {
        this.detail = detail;
    }

    public Status getStatus() {
        return status;
    }

    public String getHandlerName() {
        return handlerName;
    }

    public String getHandleRemark() {
        return handleRemark;
    }

    public LocalDateTime getHandleTime() {
        return handleTime;
    }

    public boolean isConfirmed() {
        return confirmed;
    }

    public void setConfirmed(boolean confirmed) {
        this.confirmed = confirmed;
    }
}
