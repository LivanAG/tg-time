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
    Optional<Workday> findByUserIdAndDate(UUID userId, LocalDate date);

    @EntityGraph(attributePaths = "breaks")
    List<Workday> findByUserIdAndDateBetweenOrderByDate(UUID userId, LocalDate from, LocalDate to);

    boolean existsByUserIdAndDate(UUID userId, LocalDate date);

    /** Solo las fechas con fichaje (calendario: hasWorkday), sin cargar las pausas. */
    @Query("select w.date from Workday w where w.userId = :userId and w.date between :from and :to")
    List<LocalDate> findDatesBetween(UUID userId, LocalDate from, LocalDate to);
}
