package com.controlhorario.period;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface WorkPeriodRepository extends JpaRepository<WorkPeriod, UUID> {

    List<WorkPeriod> findByUserIdOrderByStartDateDesc(UUID userId);

    Optional<WorkPeriod> findByIdAndUserId(UUID id, UUID userId);

    // Dos sentencias (primero desmarcar, luego marcar): el índice único parcial se comprueba fila a fila.
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update WorkPeriod p set p.selected = false where p.userId = :userId and p.selected = true")
    void clearSelected(UUID userId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update WorkPeriod p set p.selected = true where p.userId = :userId and p.id = :periodId")
    void markSelected(UUID userId, UUID periodId);


    /** Periodos del usuario que tocan el rango [from, to], por fecha de inicio. */
    @Query("""
            select p from WorkPeriod p
            where p.userId = :userId and p.startDate <= :to and p.endDate >= :from
            order by p.startDate""")
    List<WorkPeriod> findOverlapping(UUID userId, LocalDate from, LocalDate to);
}
