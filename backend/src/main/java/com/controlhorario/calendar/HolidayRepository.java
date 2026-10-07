package com.controlhorario.calendar;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface HolidayRepository extends JpaRepository<Holiday, UUID> {

    List<Holiday> findByPeriodIdOrderByDate(UUID periodId);

    Optional<Holiday> findByIdAndPeriodId(UUID id, UUID periodId);

    boolean existsByPeriodIdAndDate(UUID periodId, LocalDate date);
}
