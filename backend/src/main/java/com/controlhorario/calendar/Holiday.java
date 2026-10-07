package com.controlhorario.calendar;

import java.time.LocalDate;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.UuidGenerator;

@Entity
@Table(name = "holidays")
public class Holiday {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @Column(name = "period_id", nullable = false)
    private UUID periodId;

    @Column(nullable = false)
    private LocalDate date;

    @Column(nullable = false, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private HolidayScope scope;

    protected Holiday() {
    }

    public Holiday(UUID periodId, LocalDate date, String name, HolidayScope scope) {
        this.periodId = periodId;
        this.date = date;
        this.name = name;
        this.scope = scope;
    }

    public UUID getId() { return id; }
    public UUID getPeriodId() { return periodId; }
    public LocalDate getDate() { return date; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public HolidayScope getScope() { return scope; }
    public void setScope(HolidayScope scope) { this.scope = scope; }
}
