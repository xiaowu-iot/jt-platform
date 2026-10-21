package io.github.xiaowuiot.jt.server.stat;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 每台车每天一行：里程、最高速度、行驶时长、报警次数。报表和大屏都读这张表，不去扫轨迹。
 */
@Entity
@Table(name = "jt_daily_stat", uniqueConstraints = @UniqueConstraint(name = "uk_daily_stat", columnNames = {"vehicle_id", "stat_day"}))
public class DailyStat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long vehicleId;
    private Long fleetId;
    @jakarta.persistence.Column(name = "stat_day")
    private LocalDate day;

    /** 当天第一个和最后一个里程表读数（km），终端不报里程时为 0 */
    private double firstMeter;
    private double lastMeter;

    /** 按 GPS 点累加的距离（km），终端不报里程时用它 */
    private double gpsKm;

    private double maxSpeed;

    /** 速度大于 0 的累计时长（秒） */
    private long runSeconds;

    private int points;
    private int alarms;

    private LocalDateTime firstTime;
    private LocalDateTime lastTime;

    /** 当天最后一个点（WGS84），算下一段距离用 */
    private double lastLat;
    private double lastLng;

    protected DailyStat() {
    }

    public DailyStat(Long vehicleId, Long fleetId, LocalDate day) {
        this.vehicleId = vehicleId;
        this.fleetId = fleetId;
        this.day = day;
    }

    /**
     * 当天里程：里程表读数可信时用里程表，否则用 GPS 距离
     */
    public double mileageKm() {
        double meter = lastMeter - firstMeter;
        double km = firstMeter > 0 && meter >= 0 && meter < 2000 ? meter : gpsKm;
        return Math.round(km * 10) / 10.0;
    }

    public Long getId() {
        return id;
    }

    void assignId(Long id) {
        this.id = id;
    }

    public Long getVehicleId() {
        return vehicleId;
    }

    public Long getFleetId() {
        return fleetId;
    }

    public void setFleetId(Long fleetId) {
        this.fleetId = fleetId;
    }

    public LocalDate getDay() {
        return day;
    }

    public double getFirstMeter() {
        return firstMeter;
    }

    public void setFirstMeter(double firstMeter) {
        this.firstMeter = firstMeter;
    }

    public double getLastMeter() {
        return lastMeter;
    }

    public void setLastMeter(double lastMeter) {
        this.lastMeter = lastMeter;
    }

    public double getGpsKm() {
        return gpsKm;
    }

    public void setGpsKm(double gpsKm) {
        this.gpsKm = gpsKm;
    }

    public double getMaxSpeed() {
        return maxSpeed;
    }

    public void setMaxSpeed(double maxSpeed) {
        this.maxSpeed = maxSpeed;
    }

    public long getRunSeconds() {
        return runSeconds;
    }

    public void setRunSeconds(long runSeconds) {
        this.runSeconds = runSeconds;
    }

    public int getPoints() {
        return points;
    }

    public void setPoints(int points) {
        this.points = points;
    }

    public int getAlarms() {
        return alarms;
    }

    public void setAlarms(int alarms) {
        this.alarms = alarms;
    }

    public LocalDateTime getFirstTime() {
        return firstTime;
    }

    public void setFirstTime(LocalDateTime firstTime) {
        this.firstTime = firstTime;
    }

    public LocalDateTime getLastTime() {
        return lastTime;
    }

    public void setLastTime(LocalDateTime lastTime) {
        this.lastTime = lastTime;
    }

    public double getLastLat() {
        return lastLat;
    }

    public void setLastLat(double lastLat) {
        this.lastLat = lastLat;
    }

    public double getLastLng() {
        return lastLng;
    }

    public void setLastLng(double lastLng) {
        this.lastLng = lastLng;
    }
}
