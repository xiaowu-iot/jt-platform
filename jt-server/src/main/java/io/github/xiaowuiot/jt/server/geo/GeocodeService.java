package io.github.xiaowuiot.jt.server.geo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.xiaowuiot.jt.server.config.JtProperties;
import io.github.xiaowuiot.jt.server.config.Roles;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 逆地理编码：经纬度 → 地址（"广东省深圳市福田区 福华三路"）。
 * <ul>
 *   <li>tianditu：天地图，要 key（和地图底图共用 jt.map.tianditu-key）</li>
 *   <li>nominatim：OpenStreetMap 的公共服务，不要 key，但每秒最多请求一次，适合演示和小规模使用</li>
 *   <li>amap：高德 Web 服务，要 key，地址更准、带附近的地标，车多时用这个</li>
 * </ul>
 * 按坐标取 4 位小数（约 10 米）做缓存，同一个地方不重复请求；请求失败返回 null，页面上只显示经纬度。
 * 传入的是 GCJ-02 坐标（页面上用的），请求 OpenStreetMap 时转回 WGS84。
 */
@Service
@Roles.On(Roles.PLATFORM)
public class GeocodeService {

    private static final Logger log = LoggerFactory.getLogger(GeocodeService.class);
    private static final int CACHE_SIZE = 20_000;

    private final JtProperties.MapSettings cfg;
    private final ObjectMapper json;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    private final Map<String, String> cache = new LinkedHashMap<>(1024, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, String> e) {
            return size() > CACHE_SIZE;
        }
    };
    private long lastRequestAt;
    /** 地址服务连不上时，5 分钟内不再请求，免得拖慢页面 */
    private volatile long downUntil;

    public GeocodeService(JtProperties props, ObjectMapper json) {
        this.cfg = props.getMap();
        this.json = json;
    }

    /**
     * @param lat GCJ-02 纬度
     * @param lng GCJ-02 经度
     */
    public String address(double lat, double lng) {
        if (lat == 0 && lng == 0) {
            return null;
        }
        String provider = provider();
        if ("none".equals(provider)) {
            return null;
        }
        String key = String.format("%.4f,%.4f", lat, lng);
        synchronized (cache) {
            if (cache.containsKey(key)) {
                return cache.get(key);
            }
        }
        if (System.currentTimeMillis() < downUntil) {
            return null;
        }
        String addr = null;
        try {
            addr = switch (provider) {
                case "amap" -> amap(lat, lng);
                case "tianditu" -> tianditu(lat, lng);
                default -> nominatim(lat, lng);
            };
        } catch (Exception e) {
            log.debug("逆地理编码失败 {}：{}", key, e.toString());
            downUntil = System.currentTimeMillis() + 300_000;
        }
        if (addr != null) {
            synchronized (cache) {
                cache.put(key, addr);
            }
        }
        return addr;
    }

    /**
     * auto：配了高德 Web 服务 key 用高德，配了天地图 key 用天地图，都没有用 OpenStreetMap（国内网络多数连不上，连不上就只显示经纬度）
     */
    private String provider() {
        String p = cfg.getGeocoder() == null ? "none" : cfg.getGeocoder();
        if (!"auto".equals(p)) {
            return p;
        }
        if (!cfg.getAmapWebKey().isBlank()) {
            return "amap";
        }
        return cfg.getTiandituKey().isBlank() ? "nominatim" : "tianditu";
    }

    /**
     * 天地图逆地理：坐标系是 CGCS2000，和 WGS84 差别可以忽略
     */
    private String tianditu(double lat, double lng) throws Exception {
        double[] w = Geo.gcj02ToWgs84(lat, lng);
        String post = String.format("{'lon':%.6f,'lat':%.6f,'ver':1}", w[1], w[0]);
        String url = "http://api.tianditu.gov.cn/geocoder?type=geocode&postStr=" + URLEncoder.encode(post, StandardCharsets.UTF_8)
                + "&tk=" + URLEncoder.encode(cfg.getTiandituKey(), StandardCharsets.UTF_8);
        JsonNode r = get(url).path("result");
        String addr = r.path("formatted_address").asText("");
        String poi = r.path("addressComponent").path("poi").asText("");
        if (addr.isEmpty()) {
            return null;
        }
        return poi.isEmpty() || addr.contains(poi) ? addr : addr + "（" + poi + "附近）";
    }

    private String amap(double lat, double lng) throws Exception {
        String url = "https://restapi.amap.com/v3/geocode/regeo?output=JSON&extensions=base&location="
                + String.format("%.6f,%.6f", lng, lat) + "&key=" + URLEncoder.encode(cfg.getAmapWebKey(), StandardCharsets.UTF_8);
        JsonNode n = get(url);
        JsonNode r = n.path("regeocode");
        String addr = r.path("formatted_address").asText("");
        return addr.isEmpty() ? null : addr;
    }

    /**
     * OpenStreetMap 的使用规定：每秒最多 1 次、要带能识别应用的 User-Agent
     */
    private String nominatim(double lat, double lng) throws Exception {
        synchronized (this) {
            long wait = lastRequestAt + 1100 - System.currentTimeMillis();
            if (wait > 0) {
                if (wait > 3000) {
                    return null;
                }
                Thread.sleep(wait);
            }
            lastRequestAt = System.currentTimeMillis();
        }
        double[] w = Geo.gcj02ToWgs84(lat, lng);
        String url = String.format("https://nominatim.openstreetmap.org/reverse?format=jsonv2&zoom=17&accept-language=zh-CN&lat=%.6f&lon=%.6f",
                w[0], w[1]);
        JsonNode a = get(url).path("address");
        if (a.isMissingNode()) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        for (String f : new String[]{"state", "city", "city_district", "district", "county", "suburb", "town"}) {
            String v = a.path(f).asText("");
            if (!v.isEmpty() && sb.indexOf(v) < 0) {
                sb.append(v);
            }
        }
        String road = a.path("road").asText("");
        String poi = firstNonEmpty(a, "amenity", "building", "shop", "office", "neighbourhood", "village", "quarter");
        if (!road.isEmpty()) {
            sb.append(sb.length() > 0 ? " " : "").append(road);
        }
        if (!poi.isEmpty()) {
            sb.append("（").append(poi).append("附近）");
        }
        return sb.length() == 0 ? null : sb.toString();
    }

    private static String firstNonEmpty(JsonNode a, String... fields) {
        for (String f : fields) {
            String v = a.path(f).asText("");
            if (!v.isEmpty()) {
                return v;
            }
        }
        return "";
    }

    private JsonNode get(String url) throws Exception {
        HttpResponse<String> r = http.send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(4))
                .header("User-Agent", "jt-platform/0.1 (vehicle monitoring; https://github.com/xiaowu-iot/jt-platform)")
                .GET().build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (r.statusCode() / 100 != 2) {
            throw new IllegalStateException("HTTP " + r.statusCode());
        }
        return json.readTree(r.body());
    }
}
