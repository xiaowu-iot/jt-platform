package io.github.xiaowuiot.jt.server.vehicle;

/**
 * 车辆类型。限速是平台侧超速判断的默认值，车辆上可以单独设置。
 */
public enum VehicleType {

    SANITATION("环卫车", 60),
    GARBAGE("垃圾收运车", 60),
    TRANSFER("垃圾转运车", 80),
    MUCK("渣土车", 60),
    LOGISTICS("物流车", 100),
    COLD_CHAIN("冷链车", 100),
    BUS("客车", 100),
    SCHOOL_BUS("校车", 60),
    AMBULANCE("救护车", 100),
    DANGEROUS("危化品车", 80),
    TAXI("出租车", 100),
    OTHER("其他", 120),
    /** 自带压缩箱，从收集点收完直接送处理厂（放在最后：演示数据的终端号按枚举序号算，不能挪动前面的） */
    DIRECT("垃圾直运车", 60);

    private final String label;
    private final int speedLimit;

    VehicleType(String label, int speedLimit) {
        this.label = label;
        this.speedLimit = speedLimit;
    }

    public String label() {
        return label;
    }

    public int speedLimit() {
        return speedLimit;
    }
}
