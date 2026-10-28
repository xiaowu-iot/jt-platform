package io.github.xiaowuiot.jt.server.device;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

public interface DeviceRepository extends JpaRepository<Device, Long>, JpaSpecificationExecutor<Device> {

    Optional<Device> findByDeviceNo(String deviceNo);

    List<Device> findByVehicleId(Long vehicleId);

    Optional<Device> findByIccid(String iccid);
}
