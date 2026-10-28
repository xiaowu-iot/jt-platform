package io.github.xiaowuiot.jt.server.device;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 设备台账：一台终端从入库、装车、拆下维修到报废的全过程。
 * <p>
 * 设备号：808 终端是终端手机号（去掉前导 0，和消息头里的一致），新能源、排放终端是车辆 VIN。
 */
@Entity
@Table(name = "jt_device", indexes = {@Index(name = "idx_jt_device_no", columnList = "deviceNo", unique = true),
        @Index(name = "idx_jt_device_vehicle", columnList = "vehicleId")})
public class Device {

    public enum Status {
        IN_STOCK("库存"),
        INSTALLED("已安装"),
        REPAIR("维修中"),
        SCRAPPED("已报废");

        private final String label;

        Status(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 30)
    private String deviceNo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private DeviceType type = DeviceType.JT808;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Status status = Status.IN_STOCK;

    /** 安装在哪辆车上 */
    private Long vehicleId;

    /** 以下几项来自终端注册（0x0100）、查询终端属性（0x0107） */
    @Column(length = 30)
    private String terminalId;
    @Column(length = 20)
    private String makerId;
    @Column(length = 40)
    private String model;
    @Column(length = 40)
    private String hardwareVersion;
    @Column(length = 40)
    private String firmwareVersion;
    @Column(length = 20)
    private String iccid;

    /** 协议版本：2011 / 2013 / 2019 */
    private int protocolVersion;

    /** 绑定的 SIM 卡 */
    private Long simId;

    @Column(length = 60)
    private String supplier;

    private LocalDate purchaseDate;

    private LocalDate warrantyUntil;

    /** 最近一次连上平台（注册、鉴权、上报）的时间 */
    private LocalDateTime lastOnline;

    /** 没有档案的终端尝试注册的时间（在"待登记终端"里显示） */
    private LocalDateTime lastRegisterAt;

    /** 最近一次读到终端属性的时间 */
    private LocalDateTime attributesAt;

    @Column(length = 200)
    private String remark;

    private LocalDateTime createdAt = LocalDateTime.now();

    public Device() {
    }

    public Device(String deviceNo, DeviceType type) {
        this.deviceNo = deviceNo;
        this.type = type;
    }

    public Long getId() {
        return id;
    }

    public String getDeviceNo() {
        return deviceNo;
    }

    public void setDeviceNo(String deviceNo) {
        this.deviceNo = deviceNo;
    }

    public DeviceType getType() {
        return type;
    }

    public void setType(DeviceType type) {
        this.type = type;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public Long getVehicleId() {
        return vehicleId;
    }

    public void setVehicleId(Long vehicleId) {
        this.vehicleId = vehicleId;
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

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String getHardwareVersion() {
        return hardwareVersion;
    }

    public void setHardwareVersion(String hardwareVersion) {
        this.hardwareVersion = hardwareVersion;
    }

    public String getFirmwareVersion() {
        return firmwareVersion;
    }

    public void setFirmwareVersion(String firmwareVersion) {
        this.firmwareVersion = firmwareVersion;
    }

    public String getIccid() {
        return iccid;
    }

    public void setIccid(String iccid) {
        this.iccid = iccid;
    }

    public int getProtocolVersion() {
        return protocolVersion;
    }

    public void setProtocolVersion(int protocolVersion) {
        this.protocolVersion = protocolVersion;
    }

    public Long getSimId() {
        return simId;
    }

    public void setSimId(Long simId) {
        this.simId = simId;
    }

    public String getSupplier() {
        return supplier;
    }

    public void setSupplier(String supplier) {
        this.supplier = supplier;
    }

    public LocalDate getPurchaseDate() {
        return purchaseDate;
    }

    public void setPurchaseDate(LocalDate purchaseDate) {
        this.purchaseDate = purchaseDate;
    }

    public LocalDate getWarrantyUntil() {
        return warrantyUntil;
    }

    public void setWarrantyUntil(LocalDate warrantyUntil) {
        this.warrantyUntil = warrantyUntil;
    }

    public LocalDateTime getLastOnline() {
        return lastOnline;
    }

    public void setLastOnline(LocalDateTime lastOnline) {
        this.lastOnline = lastOnline;
    }

    public LocalDateTime getLastRegisterAt() {
        return lastRegisterAt;
    }

    public void setLastRegisterAt(LocalDateTime lastRegisterAt) {
        this.lastRegisterAt = lastRegisterAt;
    }

    public LocalDateTime getAttributesAt() {
        return attributesAt;
    }

    public void setAttributesAt(LocalDateTime attributesAt) {
        this.attributesAt = attributesAt;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
