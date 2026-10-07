package com.controlhorario.common.audit;

import java.util.UUID;

import com.controlhorario.common.security.ClientIp;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * audit_log para login, cambios de contraseña, importaciones y borrados.
 * Acciones: LOGIN_OK, LOGIN_FAIL, LOGOUT, TOKEN_REUSE, REGISTER, USER_CREATE, PASSWORD_CHANGE,
 * IMPORT, DELETE (entity indica qué se borró).
 */
@Service
public class AuditService {

    private final AuditLogRepository repository;

    public AuditService(AuditLogRepository repository) {
        this.repository = repository;
    }

    /** En su propia transacción: el registro sobrevive aunque la operación auditada falle. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(UUID userId, String action, String entity, String entityId) {
        repository.save(new AuditLog(userId, action, entity, entityId, ClientIp.current()));
    }
}
