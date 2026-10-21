package io.github.xiaowuiot.jt.server.fleet;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FleetRepository extends JpaRepository<Fleet, Long> {

    List<Fleet> findAllByOrderBySortAscIdAsc();

    boolean existsByParentId(Long parentId);
}
