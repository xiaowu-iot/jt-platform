package io.github.xiaowuiot.jt.server.notify;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotifyRecordRepository extends JpaRepository<NotifyRecord, Long> {

    Page<NotifyRecord> findAllByOrderByIdDesc(Pageable pageable);
}
