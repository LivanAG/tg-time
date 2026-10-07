package com.controlhorario.common.audit;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.Immutable;

/** Registro de auditoría: solo inserción. Nunca guarda tokens ni contraseñas. */
@Entity
@Immutable
@Table(name = "audit_log")
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id")
    private UUID userId;

    @Column(nullable = false, length = 50)
    private String action;

    @Column(length = 50)
    private String entity;

    @Column(name = "entity_id", length = 64)
    private String entityId;

    @Column(nullable = false)
    private Instant at = Instant.now();

    @Column(length = 45)
    private String ip;

    protected AuditLog() {
    }

    public AuditLog(UUID userId, String action, String entity, String entityId, String ip) {
        this.userId = userId;
        this.action = action;
        this.entity = entity;
        this.entityId = entityId;
        this.ip = ip;
    }

    public Long getId() { return id; }
    public UUID getUserId() { return userId; }
    public String getAction() { return action; }
    public String getEntity() { return entity; }
    public String getEntityId() { return entityId; }
    public Instant getAt() { return at; }
    public String getIp() { return ip; }
}
