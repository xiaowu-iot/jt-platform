package io.github.xiaowuiot.jt.server.device;

/**
 * 设备类型。一辆车可以装多台：定位终端（808）是主终端，决定车辆档案里的终端号；
 * 视频、主动安全有的是一体机（和定位终端是同一台），有的是单独的设备；新能源和排放终端按 VIN 接入。
 */
public enum DeviceType {

    JT808("定位终端", "JT/T 808"),
    VIDEO("视频终端", "JT/T 1078"),
    ADAS("主动安全终端", "苏标 / 粤标"),
    GB32960("新能源车载终端", "GB/T 32960"),
    HJ1239("排放远程终端", "HJ 1239"),
    OTHER("其他设备", "");

    private final String label;
    private final String protocol;

    DeviceType(String label, String protocol) {
        this.label = label;
        this.protocol = protocol;
    }

    public String label() {
        return label;
    }

    public String protocol() {
        return protocol;
    }
}
