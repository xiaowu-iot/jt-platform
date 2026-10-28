package io.github.xiaowuiot.jt.server.device;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DeviceBindingRepository extends JpaRepository<DeviceBinding, Long> {

    List<DeviceBinding> findByDeviceIdOrderByInstallTimeDesc(Long deviceId);

    List<DeviceBinding> findByVehicleIdOrderByInstallTimeDesc(Long vehicleId);

    Optional<DeviceBinding> findFirstByDeviceIdAndRemoveTimeIsNullOrderByInstallTimeDesc(Long deviceId);

    List<DeviceBinding> findTop100ByOrderByIdDesc();
}
