package io.github.xiaowuiot.jt.server.command;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface CommandRecordRepository extends JpaRepository<CommandRecord, Long>, JpaSpecificationExecutor<CommandRecord> {
}
