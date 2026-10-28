package io.github.xiaowuiot.jt.server.vehicle;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

public interface VehicleRepository extends JpaRepository<Vehicle, Long>, JpaSpecificationExecutor<Vehicle> {

    Optional<Vehicle> findByDeviceId(String deviceId);

    Optional<Vehicle> findByPlate(String plate);

    boolean existsByPlate(String plate);

    boolean existsByDeviceId(String deviceId);

    long countByFleetId(Long fleetId);
}
