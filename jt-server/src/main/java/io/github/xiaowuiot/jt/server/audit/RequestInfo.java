package io.github.xiaowuiot.jt.server.audit;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 记录用的请求信息：来源 IP、客户端、浏览器。
 */
public record RequestInfo(String ip, String client, String userAgent) {

    public static RequestInfo of(HttpServletRequest req) {
        return new RequestInfo(ip(req), client(req), cut(req.getHeader("User-Agent"), 255));
    }

    /** 只有请求来自本机反向代理时才相信 X-Real-IP / X-Forwarded-For，外部直连伪造的头不认 */
    static String ip(HttpServletRequest req) {
        String remote = req.getRemoteAddr();
        boolean proxied = "127.0.0.1".equals(remote) || "0:0:0:0:0:0:0:1".equals(remote) || "::1".equals(remote);
        if (proxied) {
            String real = req.getHeader("X-Real-IP");
            if (real != null && !real.isBlank()) {
                return cut(real.strip(), 64);
            }
            String xff = req.getHeader("X-Forwarded-For");
            if (xff != null && !xff.isBlank()) {
                return cut(xff.split(",")[0].strip(), 64);
            }
        }
        return proxied ? "127.0.0.1" : remote;
    }

    /** 按页面来源区分：手机端 /m/、数据大屏 /screen/，其余是管理后台 */
    static String client(HttpServletRequest req) {
        String ref = req.getHeader("Referer");
        if (ref != null) {
            if (ref.contains("/m/")) {
                return "手机端";
            }
            if (ref.contains("/screen/")) {
                return "数据大屏";
            }
            return "管理后台";
        }
        return "接口";
    }

    static String cut(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max);
    }
}
