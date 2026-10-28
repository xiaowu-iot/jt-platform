package io.github.xiaowuiot.jt.server.system;

/**
 * 权限点。角色由若干权限点组成，管理员可以在后台自由组合新角色。
 */
public enum Permission {

    VEHICLE_VIEW("查看车辆、位置和轨迹"),
    VEHICLE_MANAGE("管理车辆、终端和车队"),
    COMMAND_SEND("下发终端指令"),
    ALARM_VIEW("查看报警"),
    ALARM_HANDLE("处理报警"),
    RAW_VIEW("查看原始报文"),
    NOTIFY_MANAGE("通知配置"),
    SYSTEM_ADMIN("系统管理（用户、角色）");

    private final String label;

    Permission(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    /** Spring Security 里的权限名 */
    public String authority() {
        return "PERM_" + name();
    }
}
