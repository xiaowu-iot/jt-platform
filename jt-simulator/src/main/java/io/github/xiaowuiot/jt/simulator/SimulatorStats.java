package io.github.xiaowuiot.jt.simulator;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;

/**
 * 模拟器的统计数据，所有终端共用。
 */
public class SimulatorStats {

    public final AtomicInteger connected = new AtomicInteger();
    public final AtomicInteger online = new AtomicInteger();
    public final LongAdder sent = new LongAdder();
    public final LongAdder responses = new LongAdder();
    public final LongAdder commands = new LongAdder();
    public final LongAdder connectFailures = new LongAdder();
    public final LongAdder disconnects = new LongAdder();

    /**
     * 连接失败的原因：异常类型 + 消息 → 次数。压测时只看"失败了多少次"不够，要知道为什么失败
     */
    public final ConcurrentHashMap<String, LongAdder> failureReasons = new ConcurrentHashMap<>();

    public void connectFailed(Throwable cause) {
        connectFailures.increment();
        String key = cause == null ? "unknown" : cause.getClass().getSimpleName() + ": " + cause.getMessage();
        if (key.length() > 120) {
            key = key.substring(0, 120);
        }
        failureReasons.computeIfAbsent(key, k -> new LongAdder()).increment();
    }

    /**
     * 消息从发出到收到平台应答的耗时，单位微秒，用来算平均延迟
     */
    public final LongAdder latencyMicros = new LongAdder();

    private long lastSent;
    private long lastResponses;
    private long lastLatency;
    private long lastTime = System.nanoTime();

    /**
     * 两次调用之间的增量统计
     */
    public synchronized String snapshot() {
        long now = System.nanoTime();
        double seconds = (now - lastTime) / 1e9;
        long s = sent.sum();
        long r = responses.sum();
        long l = latencyMicros.sum();
        long dr = r - lastResponses;
        String line = String.format("连接 %d，在线 %d，发送 %.1f 条/秒，应答 %.1f 条/秒，平均延迟 %.2f ms，收到指令 %d，连接失败 %d，断开 %d",
                connected.get(), online.get(), (s - lastSent) / seconds, dr / seconds,
                dr == 0 ? 0 : (l - lastLatency) / 1000.0 / dr, commands.sum(), connectFailures.sum(), disconnects.sum());
        lastSent = s;
        lastResponses = r;
        lastLatency = l;
        lastTime = now;
        return line;
    }

    /**
     * 从启动到现在的累计统计
     */
    public String total() {
        long r = responses.sum();
        return String.format("累计发送 %d 条，收到应答 %d 条，平均延迟 %.2f ms，收到指令 %d，连接失败 %d，断开 %d",
                sent.sum(), r, r == 0 ? 0 : latencyMicros.sum() / 1000.0 / r, commands.sum(),
                connectFailures.sum(), disconnects.sum());
    }

    public String failureSummary() {
        StringBuilder sb = new StringBuilder();
        failureReasons.forEach((k, v) -> sb.append("\n  ").append(v.sum()).append(" 次 ").append(k));
        return sb.length() == 0 ? "无" : sb.toString();
    }

    public int online() {
        return online.get();
    }
}
