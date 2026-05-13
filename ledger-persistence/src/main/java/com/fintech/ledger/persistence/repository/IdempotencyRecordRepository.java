package com.fintech.ledger.persistence.repository;

import com.fintech.ledger.persistence.entity.IdempotencyRecordEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IdempotencyRecordRepository
    extends JpaRepository<IdempotencyRecordEntity, UUID> {

  Optional<IdempotencyRecordEntity> findByScopeAndKeyHash(String scope, String keyHash);
}
