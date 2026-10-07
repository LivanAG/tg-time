package com.controlhorario.period;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface IntensiveRangeRepository extends JpaRepository<IntensiveRange, UUID> {

    List<IntensiveRange> findByPeriodIdOrderByStartDate(UUID periodId);

    /** Rangos de varios periodos de una vez (listado de periodos sin N+1). */
    List<IntensiveRange> findByPeriodIdInOrderByStartDate(Collection<UUID> periodIds);

    @Modifying
    @Query("delete from IntensiveRange r where r.periodId = :periodId")
    int deleteByPeriodId(UUID periodId);
}
