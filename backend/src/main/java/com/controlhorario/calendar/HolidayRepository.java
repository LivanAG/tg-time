package com.controlhorario.calendar;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface HolidayRepository extends JpaRepository<Holiday, UUID> {

    List<Holiday> findByPeriodIdOrderByDate(UUID periodId);

    Optional<Holiday> findByIdAndPeriodId(UUID id, UUID periodId);

    boolean existsByPeriodIdAndDate(UUID periodId, LocalDate date);

    @Modifying
    @Query("delete from Holiday h where h.periodId = :periodId")
    int deleteByPeriodId(UUID periodId);
}
