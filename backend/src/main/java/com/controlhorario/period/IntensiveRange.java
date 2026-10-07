package com.controlhorario.period;

import java.time.LocalDate;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.UuidGenerator;

/** Rango de fechas (inclusive) con jornada intensiva. */
@Entity
@Table(name = "intensive_ranges")
public class IntensiveRange {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @Column(name = "period_id", nullable = false)
    private UUID periodId;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    protected IntensiveRange() {
    }

    public IntensiveRange(UUID periodId, LocalDate startDate, LocalDate endDate) {
        this.periodId = periodId;
        this.startDate = startDate;
        this.endDate = endDate;
    }

    public UUID getId() { return id; }
    public UUID getPeriodId() { return periodId; }
    public LocalDate getStartDate() { return startDate; }
    public LocalDate getEndDate() { return endDate; }
}
