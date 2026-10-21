package io.github.xiaowuiot.jt.server.bus;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * 上行消息总线。
 * <p>
 * {@link #publish} 返回的 future 完成，表示消息已经安全交出去了（进了队列或者写进了 Kafka），
 * 接入层这时才给终端回应答。交不出去（队列满、Kafka 故障）就不回应答，终端会重发，数据不丢。
 */
public interface UplinkBus {

    CompletableFuture<Void> publish(UplinkMessage message);

    /**
     * 订阅。回调在总线自己的线程里执行，不在 Netty 的 IO 线程里，可以写库
     */
    void subscribe(String name, Consumer<UplinkMessage> consumer);
}
