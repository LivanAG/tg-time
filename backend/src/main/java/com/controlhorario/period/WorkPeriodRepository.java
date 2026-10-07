package com.controlhorario.period;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface WorkPeriodRepository extends JpaRepository<WorkPeriod, UUID> {

    List<WorkPeriod> findByUserIdOrderByStartDateDesc(UUID userId);

    Optional<WorkPeriod> findByIdAndUserId(UUID id, UUID userId);

    @Query("select p from WorkPeriod p where p.userId = :userId and p.startDate <= :date and p.endDate >= :date")
    Optional<WorkPeriod> findContaining(UUID userId, LocalDate date);

    @Query("""
            select count(p) > 0 from WorkPeriod p
            where p.userId = :userId and p.startDate <= :end and p.endDate >= :start
              and (:excludeId is null or p.id <> :excludeId)""")
    boolean existsOverlapping(UUID userId, LocalDate start, LocalDate end, UUID excludeId);
}
