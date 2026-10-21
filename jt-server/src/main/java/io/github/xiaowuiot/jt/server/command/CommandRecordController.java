package io.github.xiaowuiot.jt.server.command;

import io.github.xiaowuiot.jt.server.common.PageResult;
import io.github.xiaowuiot.jt.server.config.Roles;
import io.github.xiaowuiot.jt.server.security.DataScope;
import io.github.xiaowuiot.jt.server.vehicle.VehicleCache;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

/**
 * 下发记录：GET /api/commands?vehicleId=&plate=&page=
 */
@RestController
@RequestMapping("/api/commands")
@Roles.On(Roles.PLATFORM)
@PreAuthorize("hasAuthority('PERM_VEHICLE_VIEW')")
public class CommandRecordController {

    private final CommandRecordRepository repo;
    private final DataScope scope;
    private final VehicleCache vehicles;

    public CommandRecordController(CommandRecordRepository repo, DataScope scope, VehicleCache vehicles) {
        this.repo = repo;
        this.scope = scope;
        this.vehicles = vehicles;
    }

    @GetMapping
    public PageResult<CommandRecord> list(@RequestParam(required = false) Long vehicleId,
                                          @RequestParam(required = false) String plate,
                                          @RequestParam(defaultValue = "0") int page,
                                          @RequestParam(defaultValue = "20") int size) {
        // 只看自己车队的车的指令记录
        java.util.Set<Long> visible = scope.fleetIds();
        List<Long> ids = visible == null ? null
                : vehicles.all().stream().filter(v -> visible.contains(v.fleetId())).map(v -> v.id()).toList();
        Specification<CommandRecord> spec = (root, q, cb) -> {
            List<Predicate> ps = new ArrayList<>();
            if (ids != null) {
                ps.add(ids.isEmpty() ? cb.disjunction() : root.get("vehicleId").in(ids));
            }
            if (vehicleId != null) {
                ps.add(cb.equal(root.get("vehicleId"), vehicleId));
            }
            if (plate != null && !plate.isBlank()) {
                ps.add(cb.like(cb.upper(root.get("plate")), "%" + plate.trim().toUpperCase() + "%"));
            }
            return cb.and(ps.toArray(new Predicate[0]));
        };
        return PageResult.of(repo.findAll(spec, PageRequest.of(page, Math.min(size, 100), Sort.by(Sort.Direction.DESC, "id"))),
                r -> r);
    }
}
