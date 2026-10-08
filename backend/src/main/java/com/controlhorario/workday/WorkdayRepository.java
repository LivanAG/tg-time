package com.controlhorario.workday;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface WorkdayRepository extends JpaRepository<Workday, UUID> {

    @EntityGraph(attributePaths = "breaks")
    Optional<Workday> findByPeriodIdAndDate(UUID periodId, LocalDate date);

    @EntityGraph(attributePaths = "breaks")
    List<Workday> findByPeriodIdAndDateBetweenOrderByDate(UUID periodId, LocalDate from, LocalDate to);

    /** Para no dejar fichajes fuera de las fechas al acortar un periodo. */
    boolean existsByPeriodIdAndDateBefore(UUID periodId, LocalDate date);

    boolean existsByPeriodIdAndDateAfter(UUID periodId, LocalDate date);

    /** Solo las fechas con fichaje (calendario: hasWorkday), sin cargar las pausas. */
    @Query("select w.date from Workday w where w.periodId = :periodId and w.date between :from and :to")
    List<LocalDate> findDatesBetween(UUID periodId, LocalDate from, LocalDate to);
}
