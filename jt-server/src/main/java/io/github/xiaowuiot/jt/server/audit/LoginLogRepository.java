package io.github.xiaowuiot.jt.server.audit;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;

public interface LoginLogRepository extends JpaRepository<LoginLog, Long>, JpaSpecificationExecutor<LoginLog> {

    @Modifying
    @Query("delete from LoginLog l where l.createdAt < ?1")
    int deleteBefore(LocalDateTime time);
}
