package io.github.xiaowuiot.jt.server.demo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 演示路线：demo/routes.json，沿深圳真实道路走的闭合路线（WGS84，取自 OpenStreetMap）。
 */
public final class DemoRoutes {

    public record Route(String name, String type, List<double[]> points) {
    }

    private DemoRoutes() {
    }

    public static Map<String, Route> load(ObjectMapper json) {
        Map<String, Route> routes = new LinkedHashMap<>();
        try (InputStream in = new ClassPathResource("demo/routes.json").getInputStream()) {
            for (JsonNode n : json.readTree(in)) {
                List<double[]> pts = new ArrayList<>();
                for (JsonNode p : n.get("points")) {
                    pts.add(new double[]{p.get(0).asDouble(), p.get(1).asDouble()});
                }
                routes.put(n.get("name").asText(), new Route(n.get("name").asText(), n.get("type").asText(), pts));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return routes;
    }
}
