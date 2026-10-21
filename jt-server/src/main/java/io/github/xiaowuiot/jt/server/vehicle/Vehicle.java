package io.github.xiaowuiot.jt.server.vehicle;

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
 * 车辆档案，和车载终端一对一绑定。
 * <p>
 * 终端用手机号（设备号）连上来，平台按设备号找到车辆；车牌、车队这些业务信息以档案为准，不以终端注册时报的为准。
 */
@Entity
@Table(name = "jt_vehicle", indexes = @Index(name = "idx_vehicle_fleet", columnList = "fleetId"))
public class Vehicle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 16)
    private String plate;

    /**
     * 车牌颜色，按 JT/T 808：1 蓝 2 黄 3 黑 4 白 5 绿 9 其他，0 未上牌
     */
    private int plateColor = 2;

    @Column(length = 17)
    private String vin;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private VehicleType type = VehicleType.OTHER;

    /**
     * 地图车标，空表示按车型显示。可选值和车型一样（VehicleType 的名字），比如环卫公司的洒水车想显示成物流车的样子
     */
    @Column(length = 16)
    private String icon;

    private Long fleetId;

    /**
     * 设备号：终端手机号去掉前导 0
     */
    @Column(unique = true, length = 20)
    private String deviceId;

    /** SIM 卡号（ICCID） */
    @Column(length = 20)
    private String simNo;

    /** 以下几项来自终端注册消息 */
    @Column(length = 30)
    private String terminalId;
    @Column(length = 11)
    private String makerId;
    @Column(length = 30)
    private String terminalModel;
    private int protocolVersion;

    @Column(length = 64)
    private String authCode;

    /** 平台侧超速判断用的限速（km/h），0 表示按车辆类型的默认值 */
    private int speedLimit;

    @Column(length = 32)
    private String driverName;
    @Column(length = 20)
    private String driverPhone;

    @Column(length = 200)
    private String remark;

    private boolean enabled = true;

    /** 演示数据：内置模拟终端沿哪条路线行驶 */
    @Column(length = 64)
    private String demoRoute;

    private LocalDateTime registeredAt;
    private LocalDateTime createdAt = LocalDateTime.now();

    protected Vehicle() {
    }

    public Vehicle(String plate, VehicleType type, String deviceId) {
        this.plate = plate;
        this.type = type;
        this.deviceId = deviceId;
    }

    public int effectiveSpeedLimit() {
        return speedLimit > 0 ? speedLimit : type.speedLimit();
    }

    public Long getId() {
        return id;
    }

    public String getPlate() {
        return plate;
    }

    public void setPlate(String plate) {
        this.plate = plate;
    }

    public int getPlateColor() {
        return plateColor;
    }

    public void setPlateColor(int plateColor) {
        this.plateColor = plateColor;
    }

    public String getVin() {
        return vin;
    }

    public void setVin(String vin) {
        this.vin = vin;
    }

    public String getIcon() {
        return icon;
    }

    public void setIcon(String icon) {
        this.icon = icon;
    }

    /**
     * 地图上实际用的车标
     */
    public String iconType() {
        return icon != null ? icon : type.name();
    }

    public VehicleType getType() {
        return type;
    }

    public void setType(VehicleType type) {
        this.type = type;
    }

    public Long getFleetId() {
        return fleetId;
    }

    public void setFleetId(Long fleetId) {
        this.fleetId = fleetId;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
    }

    public String getSimNo() {
        return simNo;
    }

    public void setSimNo(String simNo) {
        this.simNo = simNo;
    }

    public String getTerminalId() {
        return terminalId;
    }

    public void setTerminalId(String terminalId) {
        this.terminalId = terminalId;
    }

    public String getMakerId() {
        return makerId;
    }

    public void setMakerId(String makerId) {
        this.makerId = makerId;
    }

    public String getTerminalModel() {
        return terminalModel;
    }

    public void setTerminalModel(String terminalModel) {
        this.terminalModel = terminalModel;
    }

    public int getProtocolVersion() {
        return protocolVersion;
    }

    public void setProtocolVersion(int protocolVersion) {
        this.protocolVersion = protocolVersion;
    }

    public String getAuthCode() {
        return authCode;
    }

    public void setAuthCode(String authCode) {
        this.authCode = authCode;
    }

    public int getSpeedLimit() {
        return speedLimit;
    }

    public void setSpeedLimit(int speedLimit) {
        this.speedLimit = speedLimit;
    }

    public String getDriverName() {
        return driverName;
    }

    public void setDriverName(String driverName) {
        this.driverName = driverName;
    }

    public String getDriverPhone() {
        return driverPhone;
    }

    public void setDriverPhone(String driverPhone) {
        this.driverPhone = driverPhone;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getDemoRoute() {
        return demoRoute;
    }

    public void setDemoRoute(String demoRoute) {
        this.demoRoute = demoRoute;
    }

    public LocalDateTime getRegisteredAt() {
        return registeredAt;
    }

    public void setRegisteredAt(LocalDateTime registeredAt) {
        this.registeredAt = registeredAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
