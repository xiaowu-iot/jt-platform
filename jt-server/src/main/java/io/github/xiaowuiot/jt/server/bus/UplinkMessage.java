package io.github.xiaowuiot.jt.server.bus;

import io.github.xiaowuiot.protocol.jt808.Jt808Message;

/**
 * 接入层交给业务层的一条上行消息：哪台终端、什么时候、发生了什么。
 * <p>
 * 接入层只负责收和解析，不关心谁来用；业务层（地图、轨迹、报警）订阅总线，不关心数据从哪个接入节点来。
 * 单机模式总线在进程内，集群模式换成 Kafka，这两边的代码都不用改。
 *
 * @param kind        上线、离线，或者一条终端消息
 * @param deviceId    设备号（手机号去掉前导 0）
 * @param receiveTime 平台收到的时间（毫秒）
 * @param message     解析后的 808 消息，上线、离线事件为 null
 * @param remote      终端的地址
 */
public record UplinkMessage(Kind kind, String deviceId, long receiveTime, Jt808Message message, String remote) {

    public enum Kind {
        ONLINE, OFFLINE, MESSAGE,
        /** 附件服务器收齐的报警附件，不是主连接上的消息，设备号是附件连接上报的终端 ID */
        ATTACHMENT,
        /** GB/T 32960 新能源车数据，设备号是 VIN */
        EV,
        /** HJ 1239 重型柴油车排放数据，设备号是 VIN */
        EMISSION
    }

    public static UplinkMessage of(String deviceId, Jt808Message message, String remote) {
        return new UplinkMessage(Kind.MESSAGE, deviceId, System.currentTimeMillis(), message, remote);
    }

    public static UplinkMessage attachment(String deviceId, Jt808Message message, String remote) {
        return new UplinkMessage(Kind.ATTACHMENT, deviceId, System.currentTimeMillis(), message, remote);
    }

    public static UplinkMessage of(Kind kind, String deviceId, Jt808Message message, String remote) {
        return new UplinkMessage(kind, deviceId, System.currentTimeMillis(), message, remote);
    }

    public static UplinkMessage online(String deviceId, String remote) {
        return new UplinkMessage(Kind.ONLINE, deviceId, System.currentTimeMillis(), null, remote);
    }

    public static UplinkMessage offline(String deviceId, String remote) {
        return new UplinkMessage(Kind.OFFLINE, deviceId, System.currentTimeMillis(), null, remote);
    }

    public int msgId() {
        return message == null ? -1 : message.getMsgId();
    }

    public <T> T body() {
        return message.getBody();
    }
}
