package io.github.xiaowuiot.jt.server.device;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * 装车记录：哪台设备什么时候装到哪辆车、什么时候拆下来、谁操作的、为什么。设备和车辆两边都能查履历。
 */
@Entity
@Table(name = "jt_device_binding", indexes = {@Index(name = "idx_jt_binding_device", columnList = "deviceId"),
        @Index(name = "idx_jt_binding_vehicle", columnList = "vehicleId")})
public class DeviceBinding {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long deviceId;

    @Column(length = 30)
    private String deviceNo;

    private Long vehicleId;

    @Column(length = 20)
    private String plate;

    private LocalDateTime installTime;

    private LocalDateTime removeTime;

    @Column(length = 30)
    private String installBy;

    @Column(length = 30)
    private String removeBy;

    @Column(length = 100)
    private String installReason;

    @Column(length = 100)
    private String removeReason;

    public DeviceBinding() {
    }

    public DeviceBinding(Device d, Long vehicleId, String plate, String by, String reason) {
        this.deviceId = d.getId();
        this.deviceNo = d.getDeviceNo();
        this.vehicleId = vehicleId;
        this.plate = plate;
        this.installTime = LocalDateTime.now();
        this.installBy = by;
        this.installReason = reason;
    }

    public void remove(String by, String reason) {
        this.removeTime = LocalDateTime.now();
        this.removeBy = by;
        this.removeReason = reason;
    }

    public Long getId() {
        return id;
    }

    public Long getDeviceId() {
        return deviceId;
    }

    public String getDeviceNo() {
        return deviceNo;
    }

    public Long getVehicleId() {
        return vehicleId;
    }

    public String getPlate() {
        return plate;
    }

    public LocalDateTime getInstallTime() {
        return installTime;
    }

    public LocalDateTime getRemoveTime() {
        return removeTime;
    }

    public String getInstallBy() {
        return installBy;
    }

    public String getRemoveBy() {
        return removeBy;
    }

    public String getInstallReason() {
        return installReason;
    }

    public String getRemoveReason() {
        return removeReason;
    }
}
