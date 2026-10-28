package io.github.xiaowuiot.jt.server.notify;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.xiaowuiot.jt.server.alarm.Alarm;
import io.github.xiaowuiot.jt.server.alarm.AlarmService;
import io.github.xiaowuiot.jt.server.alarm.AlarmTypes;
import io.github.xiaowuiot.jt.server.config.Roles;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 报警通知。新报警产生时，按渠道配置的最低级别推送；在单独的线程里发，不拖慢报警识别。
 */
@Service
@Roles.On(Roles.PLATFORM)
public class NotifyService {

    private static final Logger log = LoggerFactory.getLogger(NotifyService.class);
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final NotifyChannelRepository channels;
    private final NotifyRecordRepository records;
    private final ObjectMapper json;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final ExecutorService sender = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "notify-sender");
        t.setDaemon(true);
        return t;
    });

    public NotifyService(NotifyChannelRepository channels, NotifyRecordRepository records, ObjectMapper json) {
        this.channels = channels;
        this.records = records;
        this.json = json;
    }

    @EventListener
    public void onAlarm(AlarmService.AlarmEvent e) {
        Alarm a = e.alarm();
        sender.execute(() -> {
            for (NotifyChannel c : channels.findByEnabledTrue()) {
                if (a.getLevel() >= c.getMinLevel()) {
                    send(c, a);
                }
            }
        });
    }

    /**
     * 异步发给所有启用的渠道（在 IO 线程上调用时用这个，不阻塞）
     */
    public void broadcastAsync(String title, String markdown) {
        sender.execute(() -> broadcast(title, markdown));
    }

    /**
     * 发给所有启用的渠道，不看报警级别（安全日报这类汇总消息用）。返回发送成功的渠道数
     */
    public int broadcast(String title, String markdown) {
        int ok = 0;
        for (NotifyChannel c : channels.findByEnabledTrue()) {
            if (deliver(c, null, title, markdown).isSuccess()) {
                ok++;
            }
        }
        return ok;
    }

    /**
     * 发一条测试消息，配置渠道时用
     */
    public NotifyRecord test(NotifyChannel c) {
        return deliver(c, null, "测试消息", "### 测试消息\n\n车辆监控平台的报警通知渠道配置成功。");
    }

    private void send(NotifyChannel c, Alarm a) {
        String title = "【" + AlarmTypes.levelName(a.getLevel()) + "】" + a.getPlate() + " " + a.getTypeName();
        String text = "### " + title + "\n\n"
                + "- 车辆：" + a.getPlate() + "\n"
                + "- 报警：" + a.getTypeName() + (a.getDetail() == null ? "" : "（" + a.getDetail() + "）") + "\n"
                + "- 时间：" + a.getStartTime().format(TIME) + "\n"
                + String.format("- 速度：%.1f km/h%n", a.getSpeed())
                + String.format("- 位置：%.6f, %.6f%n", a.getLat(), a.getLng());
        deliver(c, a.getId(), title, text);
    }

    private NotifyRecord deliver(NotifyChannel c, Long alarmId, String title, String markdown) {
        NotifyRecord r = new NotifyRecord(c.getId(), c.getName(), alarmId, title, markdown);
        try {
            String body;
            String url = c.getUrl();
            if (c.getType() == NotifyChannel.Type.DINGTALK) {
                Map<String, Object> md = new LinkedHashMap<>();
                md.put("title", title);
                md.put("text", markdown);
                body = json.writeValueAsString(Map.of("msgtype", "markdown", "markdown", md));
                url = sign(url, c.getSecret());
            } else {
                body = json.writeValueAsString(Map.of("title", title, "text", markdown, "alarmId", alarmId == null ? 0 : alarmId));
            }
            HttpResponse<String> resp = http.send(HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json; charset=utf-8")
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8)).build(),
                    HttpResponse.BodyHandlers.ofString());
            boolean ok = resp.statusCode() / 100 == 2 && (c.getType() != NotifyChannel.Type.DINGTALK
                    || resp.body().contains("\"errcode\":0"));
            r.setSuccess(ok);
            if (!ok) {
                String err = "HTTP " + resp.statusCode() + " " + resp.body();
                r.setError(err.length() > 400 ? err.substring(0, 400) : err);
            }
        } catch (Exception e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            r.setSuccess(false);
            r.setError(String.valueOf(e.getMessage()));
            log.warn("通知发送失败：{} {}", c.getName(), e.toString());
        }
        return records.save(r);
    }

    /**
     * 钉钉机器人加签：timestamp + "\n" + secret 做 HmacSHA256，Base64 后再 URL 编码
     */
    static String sign(String url, String secret) throws Exception {
        if (secret == null || secret.isBlank()) {
            return url;
        }
        long ts = System.currentTimeMillis();
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String sign = Base64.getEncoder().encodeToString(mac.doFinal((ts + "\n" + secret).getBytes(StandardCharsets.UTF_8)));
        return url + (url.contains("?") ? "&" : "?") + "timestamp=" + ts + "&sign=" + URLEncoder.encode(sign, StandardCharsets.UTF_8);
    }

    public List<NotifyChannel> channels() {
        return channels.findAll();
    }
}
