package io.github.xiaowuiot.jt.server.alarm;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;

public interface AlarmRepository extends JpaRepository<Alarm, Long>, JpaSpecificationExecutor<Alarm> {

    List<Alarm> findByEndTimeIsNull();

    List<Alarm> findTop50ByIdGreaterThanOrderByIdDesc(Long id);

    List<Alarm> findTop20ByOrderByIdDesc();

    long countByStartTimeAfter(LocalDateTime t);

    long countByStatus(Alarm.Status status);

    @Query("select a.typeName, count(a) from Alarm a where a.startTime >= ?1 group by a.typeName order by count(a) desc")
    List<Object[]> countByType(LocalDateTime from);

    @Query("select a.level, count(a) from Alarm a where a.startTime >= ?1 group by a.level")
    List<Object[]> countByLevel(LocalDateTime from);

    @Query("select a.startTime from Alarm a where a.startTime >= ?1")
    List<LocalDateTime> startTimesAfter(LocalDateTime from);

    @Query("select a.vehicleId, count(a) from Alarm a where a.startTime >= ?1 group by a.vehicleId order by count(a) desc")
    List<Object[]> countByVehicle(LocalDateTime from);
}
