package io.github.xiaowuiot.jt.server.vehicle;

import io.github.xiaowuiot.jt.server.common.ApiException;
import io.github.xiaowuiot.jt.server.common.PageResult;
import io.github.xiaowuiot.jt.server.config.Roles;
import io.github.xiaowuiot.jt.server.security.DataScope;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 车辆档案和实时状态。
 * <pre>
 * GET  /api/vehicles?keyword=&fleetId=&type=&state=   分页列表
 * GET  /api/vehicles/map                              地图上的全部车辆（精简字段）
 * GET  /api/vehicles/{id}                             一辆车
 * POST /api/vehicles  PUT /api/vehicles/{id}  DELETE /api/vehicles/{id}
 * </pre>
 */
@RestController
@RequestMapping("/api/vehicles")
@Roles.On(Roles.PLATFORM)
@PreAuthorize("hasAuthority('PERM_VEHICLE_VIEW')")
public class VehicleController {

    private final VehicleRepository repo;
    private final VehicleService service;
    private final DataScope scope;

    public VehicleController(VehicleRepository repo, VehicleService service, DataScope scope) {
        this.repo = repo;
        this.service = service;
        this.scope = scope;
    }

    @GetMapping
    public PageResult<VehicleService.VehicleView> list(@RequestParam(required = false) String keyword,
                                                       @RequestParam(required = false) Long fleetId,
                                                       @RequestParam(required = false) VehicleType type,
                                                       @RequestParam(required = false) String state,
                                                       @RequestParam(defaultValue = "0") int page,
                                                       @RequestParam(defaultValue = "20") int size) {
        Set<Long> visible = scope.fleetIds();
        Set<Long> fleetFilter = fleetId == null ? null : scope.withChildren(Set.of(fleetId));
        Specification<Vehicle> spec = (root, q, cb) -> {
            List<Predicate> ps = new ArrayList<>();
            if (keyword != null && !keyword.isBlank()) {
                String k = "%" + keyword.trim().toUpperCase() + "%";
                ps.add(cb.or(cb.like(cb.upper(root.get("plate")), k), cb.like(root.get("deviceId"), k),
                        cb.like(cb.upper(root.get("vin")), k), cb.like(root.get("driverName"), "%" + keyword.trim() + "%")));
            }
            if (visible != null) {
                ps.add(root.get("fleetId").in(visible));
            }
            if (fleetFilter != null) {
                ps.add(root.get("fleetId").in(fleetFilter));
            }
            if (type != null) {
                ps.add(cb.equal(root.get("type"), type));
            }
            return cb.and(ps.toArray(new Predicate[0]));
        };
        if (state == null || state.isBlank()) {
            Page<Vehicle> p = repo.findAll(spec, PageRequest.of(page, Math.min(size, 200), Sort.by("plate")));
            Map<Long, String> names = service.fleetNames();
            return new PageResult<>(p.getContent().stream().map(v -> service.view(v, names)).toList(),
                    p.getTotalElements(), page, size);
        }
        // 按实时状态筛选：状态不在库里，只能先查出来再过滤
        List<VehicleService.VehicleView> all = service.views(repo.findAll(spec, Sort.by("plate")));
        List<VehicleService.VehicleView> filtered = all.stream().filter(v -> matches(v.state(), state)).toList();
        int from = Math.min(page * size, filtered.size());
        return new PageResult<>(filtered.subList(from, Math.min(from + size, filtered.size())), filtered.size(), page, size);
    }

    private static boolean matches(String s, String filter) {
        return switch (filter) {
            case "ONLINE" -> !"OFFLINE".equals(s) && !"NEVER".equals(s);
            case "OFFLINE" -> "OFFLINE".equals(s) || "NEVER".equals(s);
            default -> filter.equals(s);
        };
    }

    @GetMapping("/map")
    public List<VehicleService.MapView> map() {
        return service.mapViews(scope.fleetIds());
    }

    @GetMapping("/types")
    public List<Map<String, Object>> types() {
        return Arrays.stream(VehicleType.values()).map(t -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("value", t.name());
            m.put("label", t.label());
            m.put("speedLimit", t.speedLimit());
            return m;
        }).toList();
    }

    @GetMapping("/{id}")
    public VehicleService.VehicleView get(@PathVariable Long id) {
        Vehicle v = load(id);
        return service.view(v, service.fleetNames());
    }

    @PostMapping
    @PreAuthorize("hasAuthority('PERM_VEHICLE_MANAGE')")
    public VehicleService.VehicleView create(@RequestBody VehicleService.VehicleForm f) {
        checkFleet(f.fleetId());
        return service.view(service.create(f), service.fleetNames());
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('PERM_VEHICLE_MANAGE')")
    public VehicleService.VehicleView update(@PathVariable Long id, @RequestBody VehicleService.VehicleForm f) {
        load(id);
        checkFleet(f.fleetId());
        return service.view(service.update(id, f), service.fleetNames());
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('PERM_VEHICLE_MANAGE')")
    public Map<String, Boolean> delete(@PathVariable Long id) {
        load(id);
        service.delete(id);
        return Map.of("ok", true);
    }

    private Vehicle load(Long id) {
        Vehicle v = repo.findById(id).orElseThrow(() -> ApiException.notFound("车辆不存在"));
        if (!scope.canSee(v.getFleetId())) {
            throw ApiException.forbidden("没有权限查看这辆车");
        }
        return v;
    }

    private void checkFleet(Long fleetId) {
        if (scope.fleetIds() != null && !scope.canSee(fleetId)) {
            throw ApiException.forbidden("只能把车辆放到自己负责的车队");
        }
    }
}
