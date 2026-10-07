package com.controlhorario.period;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface IntensiveRangeRepository extends JpaRepository<IntensiveRange, UUID> {

    List<IntensiveRange> findByPeriodIdOrderByStartDate(UUID periodId);

    @Modifying
    @Query("delete from IntensiveRange r where r.periodId = :periodId")
    int deleteByPeriodId(UUID periodId);
}
