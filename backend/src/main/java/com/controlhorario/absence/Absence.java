package com.controlhorario.absence;

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
@Table(name = "absences")
public class Absence {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false)
    private LocalDate date;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private AbsenceType type;

    @Column(name = "half_day", nullable = false)
    private boolean halfDay;

    @Column(length = 500)
    private String note;

    protected Absence() {
    }

    public Absence(UUID userId, LocalDate date) {
        this.userId = userId;
        this.date = date;
    }

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public LocalDate getDate() { return date; }
    public AbsenceType getType() { return type; }
    public void setType(AbsenceType type) { this.type = type; }
    public boolean isHalfDay() { return halfDay; }
    public void setHalfDay(boolean halfDay) { this.halfDay = halfDay; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
}
