package com.fintech.ledger.api;

import com.fintech.ledger.persistence.entity.FinancialTransactionEntity;
import java.time.Instant;

public class LedgerQueryDtos {

  public record TransactionSummary(
      String publicId,
      String type,
      String status,
      String currency,
      String correlationId,
      Instant createdAt) {

    static TransactionSummary from(FinancialTransactionEntity txn) {
      return new TransactionSummary(
          txn.getPublicId(),
          txn.getType().name(),
          txn.getStatus().name(),
          txn.getCurrency(),
          txn.getCorrelationId(),
          txn.getCreatedAt());
    }
  }

  public record TransactionDetail(
      String publicId,
      String type,
      String status,
      String currency,
      String correlationId,
      String description,
      Instant createdAt) {

    static TransactionDetail from(FinancialTransactionEntity txn) {
      return new TransactionDetail(
          txn.getPublicId(),
          txn.getType().name(),
          txn.getStatus().name(),
          txn.getCurrency(),
          txn.getCorrelationId(),
          txn.getDescription(),
          txn.getCreatedAt());
    }
  }
}
