package com.fintech.ledger.persistence.repository;

import com.fintech.ledger.persistence.entity.FinancialTransactionEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FinancialTransactionRepository
    extends JpaRepository<FinancialTransactionEntity, UUID> {

  Optional<FinancialTransactionEntity> findByPublicId(String publicId);

  Optional<FinancialTransactionEntity> findByIdempotencyScopeAndIdempotencyKey(
      String idempotencyScope, String idempotencyKey);
}
