package com.fintech.ledger.transaction;

import com.fintech.ledger.persistence.entity.FinancialTransactionEntity;
import java.util.UUID;

public record WithdrawOutcome(
    FinancialTransactionEntity transaction,
    UUID pendingApprovalId) {

  public boolean isPendingApproval() {
    return pendingApprovalId != null;
  }

  public static WithdrawOutcome posted(FinancialTransactionEntity txn) {
    return new WithdrawOutcome(txn, null);
  }

  public static WithdrawOutcome pendingApproval(UUID approvalId) {
    return new WithdrawOutcome(null, approvalId);
  }
}
