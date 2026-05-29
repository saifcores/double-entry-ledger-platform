package com.fintech.ledger.query;

import com.fintech.ledger.domain.AccountType;
import com.fintech.ledger.persistence.entity.AccountEntity;
import com.fintech.ledger.persistence.entity.ApprovalRequestEntity;
import com.fintech.ledger.persistence.entity.AuditLogEntity;
import com.fintech.ledger.persistence.entity.FinancialTransactionEntity;
import com.fintech.ledger.persistence.entity.JournalEntryEntity;
import com.fintech.ledger.persistence.entity.ReconciliationReportEntity;
import com.fintech.ledger.persistence.entity.WalletEntity;
import com.fintech.ledger.persistence.repository.AccountRepository;
import com.fintech.ledger.persistence.repository.ApprovalRequestRepository;
import com.fintech.ledger.persistence.repository.AuditLogRepository;
import com.fintech.ledger.persistence.repository.FinancialTransactionRepository;
import com.fintech.ledger.persistence.repository.JournalEntryRepository;
import com.fintech.ledger.persistence.repository.ReconciliationReportRepository;
import com.fintech.ledger.persistence.repository.WalletRepository;
import com.fintech.ledger.wallet.WalletService;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class LedgerQueryService {

  private final WalletRepository walletRepository;
  private final WalletService walletService;
  private final FinancialTransactionRepository financialTransactionRepository;
  private final JournalEntryRepository journalEntryRepository;
  private final AccountRepository accountRepository;
  private final ApprovalRequestRepository approvalRequestRepository;
  private final ReconciliationReportRepository reconciliationReportRepository;
  private final AuditLogRepository auditLogRepository;

  @Transactional(readOnly = true)
  public WalletBalanceView getWalletBalance(UUID userId, String currency) {
    WalletEntity wallet = walletService.requireUserWallet(userId, currency);
    return toWalletView(wallet);
  }

  @Transactional(readOnly = true)
  public List<WalletBalanceView> listUserWallets(UUID userId) {
    return walletRepository.findByUser_IdOrderByCurrencyAsc(userId).stream()
        .map(this::toWalletView)
        .toList();
  }

  @Transactional(readOnly = true)
  public FinancialTransactionEntity getTransaction(String publicId) {
    return financialTransactionRepository
        .findByPublicId(publicId)
        .orElseThrow(
            () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Transaction not found"));
  }

  @Transactional(readOnly = true)
  public void assertUserCanViewTransaction(UUID userId, FinancialTransactionEntity txn) {
    List<UUID> userAccountIds =
        walletRepository.findByUser_IdOrderByCurrencyAsc(userId).stream()
            .map(w -> w.getAccount().getId())
            .toList();
    if (userAccountIds.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied");
    }
    List<JournalEntryEntity> lines = journalEntryRepository.findByTransaction_Id(txn.getId());
    boolean involved =
        lines.stream().anyMatch(je -> userAccountIds.contains(je.getAccount().getId()));
    if (!involved) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied");
    }
  }

  @Transactional(readOnly = true)
  public List<JournalEntryView> getJournalEntries(String publicId) {
    FinancialTransactionEntity txn = getTransaction(publicId);
    return journalEntryRepository.findByTransaction_Id(txn.getId()).stream()
        .map(this::toJournalView)
        .toList();
  }

  @Transactional(readOnly = true)
  public Page<FinancialTransactionEntity> listUserTransactions(UUID userId, Pageable pageable) {
    List<UUID> accountIds =
        walletRepository.findByUser_IdOrderByCurrencyAsc(userId).stream()
            .map(w -> w.getAccount().getId())
            .toList();
    if (accountIds.isEmpty()) {
      return Page.empty(pageable);
    }
    return financialTransactionRepository.findDistinctByAccountIds(accountIds, pageable);
  }

  @Transactional(readOnly = true)
  public List<AccountView> listAccounts(String currency) {
    List<AccountEntity> accounts =
        currency == null || currency.isBlank()
            ? accountRepository.findAllByOrderByCodeAsc()
            : accountRepository.findByCurrencyOrderByCodeAsc(currency.toUpperCase());
    return accounts.stream().map(this::toAccountView).toList();
  }

  @Transactional(readOnly = true)
  public TrialBalanceView getTrialBalance(String currency) {
    String ccy = currency.toUpperCase();
    List<AccountEntity> accounts = accountRepository.findByCurrencyOrderByCodeAsc(ccy);
    Map<AccountType, Long> debits = new EnumMap<>(AccountType.class);
    Map<AccountType, Long> credits = new EnumMap<>(AccountType.class);
    long totalDebits = 0;
    long totalCredits = 0;
    List<TrialBalanceLine> lines = new ArrayList<>();
    for (AccountEntity acc : accounts) {
      long balance = acc.getBalanceMinor();
      long debit = 0;
      long credit = 0;
      if (acc.getType() == AccountType.ASSET || acc.getType() == AccountType.EXPENSE) {
        if (balance >= 0) {
          debit = balance;
        } else {
          credit = -balance;
        }
      } else {
        if (balance >= 0) {
          credit = balance;
        } else {
          debit = -balance;
        }
      }
      debits.merge(acc.getType(), debit, Long::sum);
      credits.merge(acc.getType(), credit, Long::sum);
      totalDebits += debit;
      totalCredits += credit;
      lines.add(
          new TrialBalanceLine(
              acc.getId(),
              acc.getCode(),
              acc.getName(),
              acc.getType().name(),
              debit,
              credit,
              acc.getBalanceMinor()));
    }
    return new TrialBalanceView(ccy, lines, totalDebits, totalCredits, totalDebits == totalCredits);
  }

  @Transactional(readOnly = true)
  public List<ApprovalView> listPendingApprovals() {
    return approvalRequestRepository
        .findByStatusOrderByCreatedAtDesc(ApprovalRequestEntity.ApprovalStatus.PENDING)
        .stream()
        .map(this::toApprovalView)
        .toList();
  }

  @Transactional(readOnly = true)
  public Page<ReconciliationReportView> listReconciliationReports(Pageable pageable) {
    return reconciliationReportRepository
        .findAllByOrderByCreatedAtDesc(pageable)
        .map(this::toReconciliationView);
  }

  @Transactional(readOnly = true)
  public Page<AuditLogView> listAuditLogs(Pageable pageable) {
    return auditLogRepository.findAllByOrderByCreatedAtDesc(pageable).map(this::toAuditView);
  }

  private WalletBalanceView toWalletView(WalletEntity wallet) {
    return new WalletBalanceView(
        wallet.getId(),
        wallet.getCurrency(),
        wallet.getAccount().getBalanceMinor(),
        wallet.isFrozen(),
        wallet.isFraudLocked(),
        wallet.getLabel());
  }

  private JournalEntryView toJournalView(JournalEntryEntity je) {
    return new JournalEntryView(
        je.getId(),
        je.getAccount().getId(),
        je.getAccount().getCode(),
        je.getDirection().name(),
        je.getAmountMinor(),
        je.getCurrency(),
        je.getMemo(),
        je.getCreatedAt());
  }

  private AccountView toAccountView(AccountEntity acc) {
    return new AccountView(
        acc.getId(),
        acc.getCode(),
        acc.getName(),
        acc.getType().name(),
        acc.getCurrency(),
        acc.getBalanceMinor(),
        acc.isFrozen());
  }

  private ApprovalView toApprovalView(ApprovalRequestEntity req) {
    return new ApprovalView(
        req.getId(),
        req.getOperationType(),
        req.getStatus().name(),
        req.getMakerUser().getId(),
        req.getPayload(),
        req.getCreatedAt());
  }

  private ReconciliationReportView toReconciliationView(ReconciliationReportEntity r) {
    return new ReconciliationReportView(
        r.getId(),
        r.getProvider(),
        r.getPeriodStart(),
        r.getPeriodEnd(),
        r.getStatus(),
        r.getMismatchCount(),
        r.getDetails(),
        r.getCreatedAt());
  }

  private AuditLogView toAuditView(AuditLogEntity log) {
    UUID actorId = log.getActorUser() != null ? log.getActorUser().getId() : null;
    return new AuditLogView(
        log.getId(),
        actorId,
        log.getAction(),
        log.getResourceType(),
        log.getResourceId(),
        log.getPayload(),
        log.getCreatedAt());
  }

  public record WalletBalanceView(
      UUID walletId,
      String currency,
      long balanceMinor,
      boolean frozen,
      boolean fraudLocked,
      String label) {}

  public record JournalEntryView(
      UUID id,
      UUID accountId,
      String accountCode,
      String direction,
      long amountMinor,
      String currency,
      String memo,
      Instant createdAt) {}

  public record AccountView(
      UUID id,
      String code,
      String name,
      String type,
      String currency,
      long balanceMinor,
      boolean frozen) {}

  public record TrialBalanceLine(
      UUID accountId,
      String code,
      String name,
      String type,
      long debitMinor,
      long creditMinor,
      long balanceMinor) {}

  public record TrialBalanceView(
      String currency,
      List<TrialBalanceLine> lines,
      long totalDebitsMinor,
      long totalCreditsMinor,
      boolean balanced) {}

  public record ApprovalView(
      UUID id,
      String operationType,
      String status,
      UUID makerUserId,
      Map<String, Object> payload,
      Instant createdAt) {}

  public record ReconciliationReportView(
      UUID id,
      String provider,
      Instant periodStart,
      Instant periodEnd,
      String status,
      int mismatchCount,
      Map<String, Object> details,
      Instant createdAt) {}

  public record AuditLogView(
      UUID id,
      UUID actorUserId,
      String action,
      String resourceType,
      String resourceId,
      Map<String, Object> payload,
      Instant createdAt) {}
}
