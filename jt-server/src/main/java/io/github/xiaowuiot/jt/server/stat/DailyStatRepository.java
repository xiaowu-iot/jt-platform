package io.github.xiaowuiot.jt.server.stat;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface DailyStatRepository extends JpaRepository<DailyStat, Long> {

    List<DailyStat> findByDay(LocalDate day);

    List<DailyStat> findByDayBetween(LocalDate from, LocalDate to);

    List<DailyStat> findByVehicleIdAndDayBetweenOrderByDay(Long vehicleId, LocalDate from, LocalDate to);
}
