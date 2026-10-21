package io.github.xiaowuiot.jt.server.track;

import java.time.LocalDateTime;

/**
 * 一个轨迹点。经纬度是终端上报的原始坐标（WGS84）。
 *
 * @param mileage 终端上报的里程（km），没有上报为 null
 * @param blind   盲区补传的点
 */
public record TrackPoint(long vehicleId, String deviceId, LocalDateTime gpsTime, LocalDateTime recvTime,
                         double lat, double lng, double speed, int direction, int altitude, Double mileage,
                         long alarm, long status, boolean blind) {

    public boolean accOn() {
        return (status & 1) == 1;
    }
}
