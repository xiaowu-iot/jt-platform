package io.github.xiaowuiot.jt.server.command;

import io.github.xiaowuiot.jt.server.common.PageResult;
import io.github.xiaowuiot.jt.server.config.Roles;
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

    public CommandRecordController(CommandRecordRepository repo) {
        this.repo = repo;
    }

    @GetMapping
    public PageResult<CommandRecord> list(@RequestParam(required = false) Long vehicleId,
                                          @RequestParam(required = false) String plate,
                                          @RequestParam(defaultValue = "0") int page,
                                          @RequestParam(defaultValue = "20") int size) {
        Specification<CommandRecord> spec = (root, q, cb) -> {
            List<Predicate> ps = new ArrayList<>();
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
