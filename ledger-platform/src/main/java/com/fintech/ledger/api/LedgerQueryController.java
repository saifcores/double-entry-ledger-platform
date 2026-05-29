package com.fintech.ledger.api;

import com.fintech.ledger.persistence.entity.FinancialTransactionEntity;
import com.fintech.ledger.query.LedgerQueryService;
import com.fintech.ledger.security.SecurityContextSupport;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "ledger")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
public class LedgerQueryController {

  private final LedgerQueryService queries;
  private final SecurityContextSupport security;

  @GetMapping("/wallets/me")
  public LedgerQueryService.WalletBalanceView myWallet(
      @RequestParam(defaultValue = "USD") String currency) {
    return queries.getWalletBalance(security.requireUserId(), currency);
  }

  @GetMapping("/wallets/me/list")
  public List<LedgerQueryService.WalletBalanceView> myWallets() {
    return queries.listUserWallets(security.requireUserId());
  }

  @GetMapping("/transactions/{publicId}")
  public LedgerQueryDtos.TransactionDetail transaction(@PathVariable String publicId) {
    UUID userId = security.requireUserId();
    FinancialTransactionEntity txn = queries.getTransaction(publicId);
    if (!security.requireUser().getAuthorities().stream()
        .anyMatch(a -> a.getAuthority().matches("ROLE_(ADMIN|OPERATIONS|COMPLIANCE)"))) {
      queries.assertUserCanViewTransaction(userId, txn);
    }
    return LedgerQueryDtos.TransactionDetail.from(txn);
  }

  @GetMapping("/transactions/{publicId}/journal-entries")
  public List<LedgerQueryService.JournalEntryView> journalEntries(
      @PathVariable String publicId) {
    UUID userId = security.requireUserId();
    FinancialTransactionEntity txn = queries.getTransaction(publicId);
    if (!security.requireUser().getAuthorities().stream()
        .anyMatch(a -> a.getAuthority().matches("ROLE_(ADMIN|OPERATIONS|COMPLIANCE)"))) {
      queries.assertUserCanViewTransaction(userId, txn);
    }
    return queries.getJournalEntries(publicId);
  }

  @GetMapping("/transactions/me")
  public Page<LedgerQueryDtos.TransactionSummary> myTransactions(
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    Pageable pageable = PageRequest.of(page, Math.min(size, 100));
    return queries
        .listUserTransactions(security.requireUserId(), pageable)
        .map(LedgerQueryDtos.TransactionSummary::from);
  }

  @GetMapping("/accounts")
  @PreAuthorize("hasAnyRole('ADMIN','OPERATIONS','COMPLIANCE')")
  public List<LedgerQueryService.AccountView> accounts(
      @RequestParam(required = false) String currency) {
    return queries.listAccounts(currency);
  }

  @GetMapping("/reports/trial-balance")
  @PreAuthorize("hasAnyRole('ADMIN','OPERATIONS','COMPLIANCE')")
  public LedgerQueryService.TrialBalanceView trialBalance(
      @RequestParam(defaultValue = "USD") String currency) {
    return queries.getTrialBalance(currency);
  }

  @GetMapping("/reports/reconciliation")
  @PreAuthorize("hasAnyRole('ADMIN','OPERATIONS','COMPLIANCE')")
  public Page<LedgerQueryService.ReconciliationReportView> reconciliationReports(
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return queries.listReconciliationReports(PageRequest.of(page, Math.min(size, 100)));
  }

  @GetMapping("/audit-logs")
  @PreAuthorize("hasAnyRole('ADMIN','OPERATIONS','COMPLIANCE')")
  public Page<LedgerQueryService.AuditLogView> auditLogs(
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "50") int size) {
    return queries.listAuditLogs(PageRequest.of(page, Math.min(size, 200)));
  }
}
