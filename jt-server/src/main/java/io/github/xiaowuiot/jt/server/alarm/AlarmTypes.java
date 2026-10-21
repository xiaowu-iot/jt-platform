package io.github.xiaowuiot.jt.server.alarm;

import io.github.xiaowuiot.protocol.jt808.Jt808Flags;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 报警类型和级别。
 * <p>
 * 808 的 32 个报警位，按对行车安全的影响分三级：紧急（要立即处置，比如紧急报警、碰撞侧翻、车辆被盗）、
 * 重要（违规驾驶，比如超速、疲劳、偏离路线）、一般（设备故障和预警）。
 */
public final class AlarmTypes {

    public static final int GENERAL = 1;
    public static final int IMPORTANT = 2;
    public static final int URGENT = 3;

    public static final String PLATFORM_OVERSPEED = "P_OVERSPEED";
    public static final String PLATFORM_FATIGUE = "P_FATIGUE";
    public static final String PLATFORM_FENCE_IN = "P_FENCE_IN";
    public static final String PLATFORM_FENCE_OUT = "P_FENCE_OUT";
    public static final String PLATFORM_ROUTE_OFF = "P_ROUTE_OFF";
    public static final String PLATFORM_AREA_OVERSPEED = "P_AREA_OVERSPEED";
    public static final String PLATFORM_EV_ALARM = "P_EV_ALARM";
    public static final String PLATFORM_NOX = "P_NOX";
    public static final String PLATFORM_UREA = "P_UREA";
    public static final String PLATFORM_MIL = "P_MIL";
    public static final String PLATFORM_TEMPERATURE = "P_TEMPERATURE";
    public static final String PLATFORM_TIRE = "P_TIRE";
    public static final String PLATFORM_OVERLOAD = "P_OVERLOAD";
    /** 渣土车：非核准时段运输、无准运证运输、偏离核准线路 */
    public static final String MUCK_TIME = "P_MUCK_TIME";
    public static final String MUCK_NO_PERMIT = "P_MUCK_NO_PERMIT";
    public static final String MUCK_ROUTE = "P_MUCK_ROUTE";

    private static final int[] URGENT_BITS = {0, 3, 26, 27, 28, 29, 30, 31};
    private static final int[] IMPORTANT_BITS = {1, 2, 18, 19, 20, 21, 22, 23, 25};

    private AlarmTypes() {
    }

    public static String terminalType(int bit) {
        return "T" + bit;
    }

    public static int terminalLevel(int bit) {
        for (int b : URGENT_BITS) {
            if (b == bit) {
                return URGENT;
            }
        }
        for (int b : IMPORTANT_BITS) {
            if (b == bit) {
                return IMPORTANT;
            }
        }
        return GENERAL;
    }

    public static String levelName(int level) {
        return level == URGENT ? "紧急" : level == IMPORTANT ? "重要" : "一般";
    }

    /**
     * 所有报警类型，给前端的筛选下拉框
     */
    public static Map<String, String> all() {
        Map<String, String> m = new LinkedHashMap<>();
        for (int bit = 0; bit < 32; bit++) {
            String name = Jt808Flags.alarmName(bit);
            if (!name.startsWith("保留位")) {
                m.put(terminalType(bit), name);
            }
        }
        m.put(PLATFORM_OVERSPEED, "平台超速");
        m.put(PLATFORM_FATIGUE, "平台疲劳驾驶");
        m.put(PLATFORM_FENCE_IN, "进入围栏");
        m.put(PLATFORM_FENCE_OUT, "离开围栏");
        m.put(PLATFORM_ROUTE_OFF, "偏离路线");
        m.put(PLATFORM_AREA_OVERSPEED, "区域超速");
        m.put(PLATFORM_EV_ALARM, "新能源报警");
        m.put(PLATFORM_NOX, "NOx 排放超标");
        m.put(PLATFORM_UREA, "尿素余量不足");
        m.put(PLATFORM_MIL, "排放故障灯点亮");
        m.put(PLATFORM_TEMPERATURE, "冷链温度超标");
        m.put(PLATFORM_TIRE, "胎压异常");
        m.put(PLATFORM_OVERLOAD, "车辆超载");
        m.put(MUCK_TIME, "非核准时段运输");
        m.put(MUCK_NO_PERMIT, "无准运证运输");
        m.put(MUCK_ROUTE, "偏离核准线路");
        m.putAll(ADAS);
        return m;
    }

    /**
     * 主动安全报警类型：A + 外设 ID + 报警类型，比如 A6401 前向碰撞、A6501 疲劳驾驶
     */
    public static String adasType(int peripheral, int type) {
        return String.format("A%02X%02X", peripheral, type);
    }

    private static final Map<String, String> ADAS = new LinkedHashMap<>();

    static {
        String[] adas = {"前向碰撞", "车道偏离", "车距过近", "行人碰撞", "频繁变道", "道路标识超限", "障碍物"};
        for (int i = 0; i < adas.length; i++) {
            ADAS.put(adasType(0x64, i + 1), "ADAS " + adas[i]);
        }
        String[] dsm = {"疲劳驾驶", "接打电话", "抽烟", "分神驾驶", "驾驶员异常"};
        for (int i = 0; i < dsm.length; i++) {
            ADAS.put(adasType(0x65, i + 1), "DSM " + dsm[i]);
        }
        ADAS.put(adasType(0x66, 0), "胎压监测报警");
        String[] bsd = {"后方接近", "左侧后方接近", "右侧后方接近"};
        for (int i = 0; i < bsd.length; i++) {
            ADAS.put(adasType(0x67, i + 1), "盲区 " + bsd[i]);
        }
    }
}
