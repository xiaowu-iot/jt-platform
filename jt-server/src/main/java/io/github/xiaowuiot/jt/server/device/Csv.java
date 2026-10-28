package io.github.xiaowuiot.jt.server.device;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 设备、SIM 卡导入导出用的 CSV 小工具：支持双引号包起来的字段（字段里有逗号时），导出带 BOM，Excel 直接打开不乱码。
 */
final class Csv {

    private Csv() {
    }

    static String[] split(String line) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (quoted) {
                if (c == '"' && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    cur.append('"');
                    i++;
                } else if (c == '"') {
                    quoted = false;
                } else {
                    cur.append(c);
                }
            } else if (c == '"') {
                quoted = true;
            } else if (c == ',' || c == '，' || c == '\t') {
                out.add(cur.toString().trim());
                cur.setLength(0);
            } else {
                cur.append(c);
            }
        }
        out.add(cur.toString().trim());
        return out.toArray(String[]::new);
    }

    /** 第 i 列，没有或空白返回 null */
    static String at(String[] c, int i) {
        return i < c.length && !c[i].isBlank() ? c[i].trim() : null;
    }

    /** 2026-10-21 或 2026/10/21 */
    static LocalDate date(String s) {
        if (s == null) {
            return null;
        }
        try {
            return LocalDate.parse(s.replace('/', '-').replace('.', '-'));
        } catch (Exception e) {
            throw new IllegalArgumentException("日期格式不对：" + s);
        }
    }

    static String line(String... cols) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < cols.length; i++) {
            String v = cols[i] == null ? "" : cols[i];
            if (i > 0) {
                sb.append(',');
            }
            // 长数字（ICCID、卡号）在 Excel 里会变成科学计数法，前面加个制表符当文本
            if (v.matches("\\d{12,}")) {
                v = "\t" + v;
            }
            sb.append(v.contains(",") || v.contains("\"") ? "\"" + v.replace("\"", "\"\"") + "\"" : v);
        }
        return sb.append('\n').toString();
    }

    static ResponseEntity<byte[]> download(String name, StringBuilder sb) {
        return ResponseEntity.ok().contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + URLEncoder.encode(name, StandardCharsets.UTF_8) + ".csv")
                .body(sb.toString().getBytes(StandardCharsets.UTF_8));
    }
}
