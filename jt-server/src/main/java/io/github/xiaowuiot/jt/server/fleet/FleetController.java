package io.github.xiaowuiot.jt.server.fleet;

import io.github.xiaowuiot.jt.server.common.ApiException;
import io.github.xiaowuiot.jt.server.config.Roles;
import io.github.xiaowuiot.jt.server.vehicle.VehicleRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 车队（分组）管理。列表是扁平的，带 parentId，前端自己组装成树。
 */
@RestController
@RequestMapping("/api/fleets")
@Roles.On(Roles.PLATFORM)
public class FleetController {

    private final FleetRepository fleets;
    private final VehicleRepository vehicles;

    public FleetController(FleetRepository fleets, VehicleRepository vehicles) {
        this.fleets = fleets;
        this.vehicles = vehicles;
    }

    public record FleetForm(@NotBlank(message = "请填写车队名称") String name, Long parentId, Integer sort,
                            String contact, String phone, String remark) {
    }

    @GetMapping
    public List<Fleet> list() {
        return fleets.findAllByOrderBySortAscIdAsc();
    }

    @PostMapping
    @PreAuthorize("hasAuthority('PERM_VEHICLE_MANAGE')")
    @Transactional
    public Fleet create(@Valid @RequestBody FleetForm f) {
        Fleet fleet = new Fleet(f.name().trim(), f.parentId(), f.sort() == null ? 0 : f.sort());
        apply(fleet, f);
        return fleets.save(fleet);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('PERM_VEHICLE_MANAGE')")
    @Transactional
    public Fleet update(@PathVariable Long id, @Valid @RequestBody FleetForm f) {
        Fleet fleet = fleets.findById(id).orElseThrow(() -> ApiException.notFound("车队不存在"));
        if (id.equals(f.parentId())) {
            throw ApiException.badRequest("上级车队不能是自己");
        }
        fleet.setName(f.name().trim());
        fleet.setParentId(f.parentId());
        fleet.setSort(f.sort() == null ? 0 : f.sort());
        apply(fleet, f);
        return fleets.save(fleet);
    }

    private static void apply(Fleet fleet, FleetForm f) {
        fleet.setContact(f.contact());
        fleet.setPhone(f.phone());
        fleet.setRemark(f.remark());
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('PERM_VEHICLE_MANAGE')")
    @Transactional
    public Map<String, Boolean> delete(@PathVariable Long id) {
        if (fleets.existsByParentId(id)) {
            throw ApiException.badRequest("请先删除下级车队");
        }
        if (vehicles.countByFleetId(id) > 0) {
            throw ApiException.badRequest("车队下还有车辆，请先移到其他车队");
        }
        fleets.deleteById(id);
        return Map.of("ok", true);
    }
}
