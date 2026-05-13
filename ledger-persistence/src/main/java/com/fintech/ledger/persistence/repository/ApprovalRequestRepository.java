package com.fintech.ledger.persistence.repository;

import com.fintech.ledger.persistence.entity.ApprovalRequestEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ApprovalRequestRepository
        extends JpaRepository<ApprovalRequestEntity, UUID> {
}
