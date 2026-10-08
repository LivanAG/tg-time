package com.controlhorario.absence;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AbsenceRepository extends JpaRepository<Absence, UUID> {

    Optional<Absence> findByPeriodIdAndDate(UUID periodId, LocalDate date);

    List<Absence> findByPeriodIdAndDateBetweenOrderByDate(UUID periodId, LocalDate from, LocalDate to);

    /** Para no dejar ausencias fuera de las fechas al acortar un periodo. */
    boolean existsByPeriodIdAndDateBefore(UUID periodId, LocalDate date);

    boolean existsByPeriodIdAndDateAfter(UUID periodId, LocalDate date);
}
