package io.github.xiaowuiot.jt.server.geo;

import io.github.xiaowuiot.jt.server.config.Roles;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * 后台页面用的逆地理编码（GCJ-02 坐标）
 */
@RestController
@Roles.On(Roles.PLATFORM)
public class GeocodeController {

    private final GeocodeService geocode;

    public GeocodeController(GeocodeService geocode) {
        this.geocode = geocode;
    }

    @GetMapping("/api/geo/address")
    @PreAuthorize("hasAuthority('PERM_VEHICLE_VIEW')")
    public Map<String, Object> address(@RequestParam double lat, @RequestParam double lng) {
        Map<String, Object> m = new HashMap<>();
        m.put("address", geocode.address(lat, lng));
        return m;
    }
}
