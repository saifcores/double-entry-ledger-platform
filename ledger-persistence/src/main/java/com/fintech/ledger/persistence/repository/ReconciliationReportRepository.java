package com.fintech.ledger.persistence.repository;

import com.fintech.ledger.persistence.entity.ReconciliationReportEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReconciliationReportRepository
        extends JpaRepository<ReconciliationReportEntity, UUID> {
}
