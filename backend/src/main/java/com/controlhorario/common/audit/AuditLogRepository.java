package com.controlhorario.common.audit;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    List<AuditLog> findByUserIdOrderByAtDesc(UUID userId);

    List<AuditLog> findByActionOrderByAtDesc(String action);
}
