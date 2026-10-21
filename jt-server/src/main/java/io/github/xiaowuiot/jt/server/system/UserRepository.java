package io.github.xiaowuiot.jt.server.system;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUsername(String username);

    boolean existsByUsername(String username);

    @Query("select distinct u from User u join u.roles r where r.id = ?1 and u.enabled = true order by u.id")
    List<User> findEnabledByRole(Long roleId);

    List<User> findByEnabledTrueOrderById();
}
