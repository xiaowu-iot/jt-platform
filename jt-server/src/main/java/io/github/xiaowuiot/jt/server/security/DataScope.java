package io.github.xiaowuiot.jt.server.security;

import io.github.xiaowuiot.jt.server.fleet.Fleet;
import io.github.xiaowuiot.jt.server.fleet.FleetRepository;
import io.github.xiaowuiot.jt.server.system.User;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 数据权限：用户只能看到自己负责的车队（含下级车队）的车。没有指定车队的用户能看全部。
 */
@Component
public class DataScope {

    private final CurrentUser current;
    private final FleetRepository fleets;

    public DataScope(CurrentUser current, FleetRepository fleets) {
        this.current = current;
        this.fleets = fleets;
    }

    /**
     * @return 能看的车队 ID（含下级），null 表示不限
     */
    public Set<Long> fleetIds() {
        User u = current.get();
        if (u.getFleetIds().isEmpty()) {
            return null;
        }
        return withChildren(u.getFleetIds());
    }

    public boolean canSee(Long fleetId) {
        Set<Long> ids = fleetIds();
        return ids == null || (fleetId != null && ids.contains(fleetId));
    }

    public Set<Long> withChildren(Set<Long> roots) {
        List<Fleet> all = fleets.findAll();
        Set<Long> result = new HashSet<>(roots);
        Deque<Long> todo = new ArrayDeque<>(roots);
        while (!todo.isEmpty()) {
            Long id = todo.pop();
            for (Fleet f : all) {
                if (id.equals(f.getParentId()) && result.add(f.getId())) {
                    todo.push(f.getId());
                }
            }
        }
        return result;
    }
}
