package com.fintech.ledger.persistence.repository;

import com.fintech.ledger.persistence.entity.FinancialTransactionEntity;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FinancialTransactionRepository
    extends JpaRepository<FinancialTransactionEntity, UUID> {

  Optional<FinancialTransactionEntity> findByPublicId(String publicId);

  Optional<FinancialTransactionEntity> findByIdempotencyScopeAndIdempotencyKey(
      String idempotencyScope, String idempotencyKey);

  @Query(
      """
      SELECT DISTINCT t FROM FinancialTransactionEntity t
      JOIN JournalEntryEntity je ON je.transaction = t
      WHERE je.account.id IN :accountIds
      ORDER BY t.createdAt DESC
      """)
  Page<FinancialTransactionEntity> findDistinctByAccountIds(
      @Param("accountIds") Collection<UUID> accountIds, Pageable pageable);
}
