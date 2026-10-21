package io.github.xiaowuiot.jt.server.device;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

public interface SimCardRepository extends JpaRepository<SimCard, Long>, JpaSpecificationExecutor<SimCard> {

    Optional<SimCard> findByIccid(String iccid);

    Optional<SimCard> findByDeviceId(Long deviceId);
}
