package com.fintech.ledger.persistence.repository;

import com.fintech.ledger.persistence.entity.AuditLogEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditLogRepository extends JpaRepository<AuditLogEntity, UUID> {
}
