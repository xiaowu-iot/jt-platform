package io.github.xiaowuiot.jt.server.common;

import java.util.regex.Pattern;

/**
 * 手机号脱敏：接口里默认只给 138****0002 这种形式，要看完整号码走图片验证码。
 */
public final class Mask {

    private static final Pattern MOBILE = Pattern.compile("(?<!\\d)(1\\d{2})\\d{4}(\\d{4})(?!\\d)");

    private Mask() {
    }

    public static String phone(String p) {
        if (p == null || p.isBlank()) {
            return p;
        }
        String s = p.trim();
        if (s.length() < 7) {
            return "****";
        }
        return s.substring(0, 3) + "****" + s.substring(s.length() - 4);
    }

    /** 把一段文字里的手机号都脱敏，比如钉钉消息里的 @手机号、发送记录的接收人 */
    public static String phonesIn(String text) {
        return text == null ? null : MOBILE.matcher(text).replaceAll("$1****$2");
    }

    /** 前端回传的还是脱敏后的号码，说明没改 */
    public static boolean isMasked(String p) {
        return p != null && p.contains("*");
    }
}
