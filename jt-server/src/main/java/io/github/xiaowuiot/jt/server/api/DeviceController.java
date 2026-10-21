package io.github.xiaowuiot.jt.server.api;

import io.github.xiaowuiot.jt.server.config.Roles;
import io.github.xiaowuiot.jt.server.platform.DeviceState;
import io.github.xiaowuiot.jt.server.platform.DeviceStateService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 终端的最新状态。
 * <pre>
 * GET /api/devices               所有终端（在线状态 + 最后位置）
 * GET /api/devices/13800138000   一台终端
 * </pre>
 */
@PreAuthorize("hasAuthority('PERM_VEHICLE_VIEW')")
@RestController
@RequestMapping("/api/devices")
@Roles.On(Roles.PLATFORM)
public class DeviceController {

    private final DeviceStateService states;

    public DeviceController(DeviceStateService states) {
        this.states = states;
    }

    @GetMapping
    public List<DeviceState> list() {
        return states.all();
    }

    @GetMapping("/{deviceId}")
    public ResponseEntity<DeviceState> get(@PathVariable String deviceId) {
        DeviceState s = states.get(deviceId);
        return s == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(s);
    }
}
