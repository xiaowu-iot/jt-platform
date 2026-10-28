package io.github.xiaowuiot.jt.server.bus;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Consumer;

/**
 * 进程内总线：一个有界队列 + 一个消费线程。单机模式用它。
 * <p>
 * 为什么不直接在 IO 线程里调用业务代码：业务代码会写库，写库慢了 IO 线程就卡住，同一个线程上的几千台终端都收不到应答。
 * 中间隔一个队列，IO 线程只管放进去就返回。
 * <p>
 * 队列有界：业务层处理不过来时，与其让内存一直涨到 OOM，不如拒绝新消息、不回应答，让终端过一会儿重发。
 */
public class LocalUplinkBus implements UplinkBus, AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(LocalUplinkBus.class);

    private final BlockingQueue<UplinkMessage> queue;
    private final List<Subscriber> subscribers = new CopyOnWriteArrayList<>();
    private final Thread worker;
    private final LongAdder published = new LongAdder();
    private final LongAdder rejected = new LongAdder();
    private volatile boolean running = true;

    public LocalUplinkBus(int capacity) {
        this.queue = new ArrayBlockingQueue<>(capacity);
        this.worker = new Thread(this::loop, "uplink-bus");
        this.worker.setDaemon(true);
        this.worker.start();
    }

    @Override
    public CompletableFuture<Void> publish(UplinkMessage message) {
        if (!queue.offer(message)) {
            rejected.increment();
            return CompletableFuture.failedFuture(new IllegalStateException("上行队列已满，" + queue.size() + " 条待处理"));
        }
        published.increment();
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public void subscribe(String name, Consumer<UplinkMessage> consumer) {
        subscribers.add(new Subscriber(name, consumer));
    }

    private void loop() {
        while (running || !queue.isEmpty()) {
            UplinkMessage m;
            try {
                m = queue.poll(500, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            if (m == null) {
                continue;
            }
            for (Subscriber s : subscribers) {
                try {
                    s.consumer.accept(m);
                } catch (RuntimeException e) {
                    // 一个订阅者出错不能影响其他订阅者，也不能让消费线程退出
                    log.warn("订阅者 {} 处理 {} 失败", s.name, m.message().getHeader(), e);
                }
            }
        }
    }

    public int pending() {
        return queue.size();
    }

    public long published() {
        return published.sum();
    }

    public long rejected() {
        return rejected.sum();
    }

    @Override
    public void close() throws InterruptedException {
        running = false;
        worker.join(5000);
    }

    private record Subscriber(String name, Consumer<UplinkMessage> consumer) {
    }
}
