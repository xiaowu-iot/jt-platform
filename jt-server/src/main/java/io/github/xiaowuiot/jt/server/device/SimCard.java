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

/**
 * 物联网卡：ICCID、卡号、运营商、套餐、到期时间、本月流量。流量可以手工录入或批量导入（运营商平台导出的表）。
 */
@Entity
@Table(name = "jt_sim", indexes = @Index(name = "idx_jt_sim_iccid", columnList = "iccid", unique = true))
public class SimCard {

    public enum Status {
        IN_STOCK("库存"),
        IN_USE("使用中"),
        SUSPENDED("停机"),
        CANCELED("已销户");

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

    @Column(nullable = false, length = 20)
    private String iccid;

    /** 卡号（MSISDN），物联网卡一般是 13 位 */
    @Column(length = 20)
    private String msisdn;

    /** 移动 / 联通 / 电信 */
    @Column(length = 10)
    private String operator;

    /** 套餐流量（MB/月） */
    private int planMb;

    /** 本月已用流量（MB） */
    private double usedMb;

    private LocalDate activateDate;

    private LocalDate expireDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Status status = Status.IN_STOCK;

    /** 插在哪台设备上 */
    private Long deviceId;

    @Column(length = 200)
    private String remark;

    public SimCard() {
    }

    public SimCard(String iccid) {
        this.iccid = iccid;
    }

    public Long getId() {
        return id;
    }

    public String getIccid() {
        return iccid;
    }

    public void setIccid(String iccid) {
        this.iccid = iccid;
    }

    public String getMsisdn() {
        return msisdn;
    }

    public void setMsisdn(String msisdn) {
        this.msisdn = msisdn;
    }

    public String getOperator() {
        return operator;
    }

    public void setOperator(String operator) {
        this.operator = operator;
    }

    public int getPlanMb() {
        return planMb;
    }

    public void setPlanMb(int planMb) {
        this.planMb = planMb;
    }

    public double getUsedMb() {
        return usedMb;
    }

    public void setUsedMb(double usedMb) {
        this.usedMb = usedMb;
    }

    public LocalDate getActivateDate() {
        return activateDate;
    }

    public void setActivateDate(LocalDate activateDate) {
        this.activateDate = activateDate;
    }

    public LocalDate getExpireDate() {
        return expireDate;
    }

    public void setExpireDate(LocalDate expireDate) {
        this.expireDate = expireDate;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public Long getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(Long deviceId) {
        this.deviceId = deviceId;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }
}
