package io.github.xiaowuiot.jt.server.command;

import io.github.xiaowuiot.protocol.jt808.Jt808Message;

import java.util.concurrent.CompletableFuture;

/**
 * 平台给终端下发指令的入口。
 * <p>
 * 单机模式终端就连在本进程上，直接找会话发；集群模式终端可能连在别的接入节点上，要先查它在哪个节点，再转过去。
 * 业务代码只认这个接口，不关心是哪种部署方式。
 */
public interface CommandGateway {

    /**
     * 下发并等待应答
     *
     * @param body 消息体，没有消息体的指令传 null
     * @return 终端的应答；不在线、超时、发送失败都以异常结束
     */
    CompletableFuture<Jt808Message> send(String deviceId, int msgId, Object body, int timeoutSeconds);

    boolean isOnline(String deviceId);
}
