package com.controlhorario.workday;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import org.hibernate.annotations.OptimisticLock;
import org.hibernate.annotations.UuidGenerator;

/** Registro de un día trabajado (una fila de la hoja mensual). */
@Entity
@Table(name = "workdays")
public class Workday {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false)
    private LocalDate date;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 8)
    private Location location = Location.OFICINA;

    @Column(name = "remote_minutes")
    private Integer remoteMinutes;

    @Column(length = 500)
    private String notes;

    // Colección inversa: sin excluded = false, cambiar solo las pausas no subiría la versión del día.
    @OneToMany(mappedBy = "workday", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("startTime")
    @OptimisticLock(excluded = false)
    private List<WorkdayBreak> breaks = new ArrayList<>();

    @Version
    private Long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected Workday() {
    }

    public Workday(UUID userId, LocalDate date) {
        this.userId = userId;
        this.date = date;
    }

    @PreUpdate
    void touch() {
        updatedAt = Instant.now();
    }

    /** Sustituye todas las pausas (PUT idempotente). */
    public void replaceBreaks(List<WorkdayBreak> newBreaks) {
        breaks.clear();
        newBreaks.stream()
                .sorted(Comparator.comparing(WorkdayBreak::getStartTime))
                .forEach(b -> {
                    b.setWorkday(this);
                    breaks.add(b);
                });
    }

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public LocalDate getDate() { return date; }
    public LocalTime getStartTime() { return startTime; }
    public void setStartTime(LocalTime startTime) { this.startTime = startTime; }
    public LocalTime getEndTime() { return endTime; }
    public void setEndTime(LocalTime endTime) { this.endTime = endTime; }
    public Location getLocation() { return location; }
    public void setLocation(Location location) { this.location = location; }
    public Integer getRemoteMinutes() { return remoteMinutes; }
    public void setRemoteMinutes(Integer remoteMinutes) { this.remoteMinutes = remoteMinutes; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public List<WorkdayBreak> getBreaks() { return breaks; }
    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
