package com.controlhorario.workday;

import java.time.LocalTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import org.hibernate.annotations.UuidGenerator;

@Entity
@Table(name = "workday_breaks")
public class WorkdayBreak {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "workday_id", nullable = false)
    private Workday workday;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private BreakType type;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    protected WorkdayBreak() {
    }

    public WorkdayBreak(BreakType type, LocalTime startTime, LocalTime endTime) {
        this.type = type;
        this.startTime = startTime;
        this.endTime = endTime;
    }

    void setWorkday(Workday workday) { this.workday = workday; }

    public UUID getId() { return id; }
    public Workday getWorkday() { return workday; }
    public BreakType getType() { return type; }
    public LocalTime getStartTime() { return startTime; }
    public LocalTime getEndTime() { return endTime; }
}
