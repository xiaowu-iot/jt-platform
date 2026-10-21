package io.github.xiaowuiot.jt.server.vehicle;

/**
 * 车辆档案在内存里的快照，接入层和总线线程只读它，不查库。
 */
public record VehicleInfo(Long id, String plate, int plateColor, VehicleType type, Long fleetId, String deviceId,
                          String authCode, int protocolVersion, boolean enabled, int speedLimit, String icon) {

    public static VehicleInfo of(Vehicle v) {
        return new VehicleInfo(v.getId(), v.getPlate(), v.getPlateColor(), v.getType(), v.getFleetId(), v.getDeviceId(),
                v.getAuthCode(), v.getProtocolVersion(), v.isEnabled(), v.effectiveSpeedLimit(), v.iconType());
    }
}
