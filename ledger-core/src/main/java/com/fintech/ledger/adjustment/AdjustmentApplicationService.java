package com.fintech.ledger.adjustment;

import com.fintech.ledger.audit.AuditService;
import com.fintech.ledger.config.LedgerProperties;
import com.fintech.ledger.domain.FinancialTxnType;
import com.fintech.ledger.domain.JournalDirection;
import com.fintech.ledger.ledger.LedgerPostingService;
import com.fintech.ledger.ledger.PostingLine;
import com.fintech.ledger.messaging.OutboxService;
import com.fintech.ledger.persistence.entity.AccountEntity;
import com.fintech.ledger.persistence.entity.FinancialTransactionEntity;
import com.fintech.ledger.persistence.repository.AccountRepository;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class AdjustmentApplicationService {

  private final LedgerPostingService ledgerPostingService;
  private final AccountRepository accountRepository;
  private final AuditService auditService;
  private final OutboxService outboxService;
  private final LedgerProperties ledgerProperties;

  @Transactional
  public FinancialTransactionEntity postAdjustment(
      UUID actorUserId,
      UUID debitAccountId,
      UUID creditAccountId,
      long amountMinor,
      String currency,
      String reason,
      String idempotencyKey) {
    if (amountMinor <= 0) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "amount must be positive");
    }
    String ccy = currency.toUpperCase();
    AccountEntity debit =
        accountRepository
            .findById(debitAccountId)
            .orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Debit account missing"));
    AccountEntity credit =
        accountRepository
            .findById(creditAccountId)
            .orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Credit account missing"));
    if (debit.isFrozen() || credit.isFrozen()) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Account frozen");
    }
    if (!debit.getCurrency().equals(ccy) || !credit.getCurrency().equals(ccy)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Currency mismatch");
    }
    FinancialTransactionEntity txn =
        ledgerPostingService.postBalancedTransaction(
            FinancialTxnType.ADJUSTMENT,
            ccy,
            "adjustment",
            idempotencyKey,
            UUID.randomUUID().toString(),
            null,
            reason != null ? reason : "Manual adjustment",
            Map.of(
                "debitAccountId",
                debitAccountId,
                "creditAccountId",
                creditAccountId,
                "reason",
                reason != null ? reason : ""),
            List.of(
                new PostingLine(debitAccountId, JournalDirection.DEBIT, amountMinor, reason),
                new PostingLine(
                    creditAccountId, JournalDirection.CREDIT, amountMinor, reason)));
    auditService.record(
        actorUserId,
        "ADJUSTMENT_POSTED",
        "TRANSACTION",
        txn.getPublicId(),
        Map.of(
            "debitAccountId",
            debitAccountId,
            "creditAccountId",
            creditAccountId,
            "amountMinor",
            amountMinor));
    outboxService.enqueue(
        ledgerProperties.getKafka().getTransactionCompleted(),
        txn.getPublicId(),
        Map.of(
            "publicId",
            txn.getPublicId(),
            "type",
            txn.getType().name(),
            "currency",
            ccy));
    return txn;
  }
}
