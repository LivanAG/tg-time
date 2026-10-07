package com.controlhorario.period;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import org.hibernate.annotations.UuidGenerator;

/** Periodo anual con sus parámetros (equivale a la hoja Horas del Excel). */
@Entity
@Table(name = "work_periods")
public class WorkPeriod {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(name = "agreement_minutes", nullable = false)
    private int agreementMinutes;

    @Column(name = "vacation_days", nullable = false)
    private int vacationDays;

    @Column(name = "normal_day_minutes", nullable = false)
    private int normalDayMinutes;

    @Column(name = "intensive_day_minutes", nullable = false)
    private int intensiveDayMinutes;

    @Column(name = "breakfast_tolerance_min", nullable = false)
    private int breakfastToleranceMin;

    @Column(name = "min_lunch_min", nullable = false)
    private int minLunchMin;

    @Column(name = "rounding_step_min", nullable = false)
    private int roundingStepMin;

    @Column(name = "max_remote_pct", nullable = false)
    private int maxRemotePct;

    @Column(name = "opening_balance_min", nullable = false)
    private int openingBalanceMin;

    @Version
    private Long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public boolean contains(LocalDate date) {
        return !date.isBefore(startDate) && !date.isAfter(endDate);
    }

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate startDate) { this.startDate = startDate; }
    public LocalDate getEndDate() { return endDate; }
    public void setEndDate(LocalDate endDate) { this.endDate = endDate; }
    public int getAgreementMinutes() { return agreementMinutes; }
    public void setAgreementMinutes(int agreementMinutes) { this.agreementMinutes = agreementMinutes; }
    public int getVacationDays() { return vacationDays; }
    public void setVacationDays(int vacationDays) { this.vacationDays = vacationDays; }
    public int getNormalDayMinutes() { return normalDayMinutes; }
    public void setNormalDayMinutes(int normalDayMinutes) { this.normalDayMinutes = normalDayMinutes; }
    public int getIntensiveDayMinutes() { return intensiveDayMinutes; }
    public void setIntensiveDayMinutes(int intensiveDayMinutes) { this.intensiveDayMinutes = intensiveDayMinutes; }
    public int getBreakfastToleranceMin() { return breakfastToleranceMin; }
    public void setBreakfastToleranceMin(int breakfastToleranceMin) { this.breakfastToleranceMin = breakfastToleranceMin; }
    public int getMinLunchMin() { return minLunchMin; }
    public void setMinLunchMin(int minLunchMin) { this.minLunchMin = minLunchMin; }
    public int getRoundingStepMin() { return roundingStepMin; }
    public void setRoundingStepMin(int roundingStepMin) { this.roundingStepMin = roundingStepMin; }
    public int getMaxRemotePct() { return maxRemotePct; }
    public void setMaxRemotePct(int maxRemotePct) { this.maxRemotePct = maxRemotePct; }
    public int getOpeningBalanceMin() { return openingBalanceMin; }
    public void setOpeningBalanceMin(int openingBalanceMin) { this.openingBalanceMin = openingBalanceMin; }
    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }
    public Instant getCreatedAt() { return createdAt; }
}
