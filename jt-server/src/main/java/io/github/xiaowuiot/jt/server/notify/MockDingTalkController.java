package io.github.xiaowuiot.jt.server.notify;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

/**
 * 内置的钉钉机器人模拟接口：没有真实的钉钉群也能演示通知，并且和真实接口一样校验加签。
 * <p>
 * 只接受本机调用（部署时 Nginx 也会挡掉 /mock/），避免被外部当成转发入口。
 */
@RestController
public class MockDingTalkController {

    public static final String SECRET = "SECmock-dingtalk-demo";

    @PostMapping("/mock/dingtalk/robot/send")
    public ResponseEntity<Map<String, Object>> send(@RequestParam(required = false) Long timestamp,
                                                    @RequestParam(required = false) String sign,
                                                    @RequestBody Map<String, Object> body, HttpServletRequest req)
            throws Exception {
        String ip = req.getRemoteAddr();
        if (!"127.0.0.1".equals(ip) && !"0:0:0:0:0:0:0:1".equals(ip)) {
            return ResponseEntity.status(403).build();
        }
        if (timestamp == null || sign == null || Math.abs(System.currentTimeMillis() - timestamp) > 3600_000) {
            return ResponseEntity.ok(Map.of("errcode", 310000, "errmsg", "sign not match"));
        }
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String expected = Base64.getEncoder().encodeToString(
                mac.doFinal((timestamp + "\n" + SECRET).getBytes(StandardCharsets.UTF_8)));
        if (!expected.equals(sign)) {
            return ResponseEntity.ok(Map.of("errcode", 310000, "errmsg", "sign not match"));
        }
        return ResponseEntity.ok(Map.of("errcode", 0, "errmsg", "ok"));
    }
}
