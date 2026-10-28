package io.github.xiaowuiot.jt.server.vehicle;

/**
 * 车辆档案和终端接入发出的事件，设备台账（device 包）监听它们保持同步，车辆这边不依赖设备模块。
 */
public final class VehicleEvents {

    private VehicleEvents() {
    }

    /**
     * 车辆档案里的终端号或 SIM 卡号变了（新建、编辑车辆）
     */
    public record DeviceChanged(Long vehicleId, String plate, String oldDeviceNo, String newDeviceNo, String simNo, String operator) {
    }

    /** 车辆删除了，装在上面的设备要拆下来 */
    public record Deleted(Long vehicleId, String plate, String operator) {
    }

    /**
     * 终端发来注册（0x0100）
     *
     * @param vehicleId 有档案时是车辆 ID；没有档案（没开自动建档，注册被拒绝）时为空，设备进"待登记终端"
     */
    public record TerminalRegistered(String deviceNo, Long vehicleId, String terminalId, String makerId, String model, String plate,
                                     int protocolVersion) {
    }
}
