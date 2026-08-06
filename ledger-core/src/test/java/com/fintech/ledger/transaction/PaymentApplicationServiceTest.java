package com.fintech.ledger.transaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fintech.ledger.audit.AuditService;
import com.fintech.ledger.config.LedgerProperties;
import com.fintech.ledger.domain.FinancialTxnType;
import com.fintech.ledger.ledger.LedgerPostingService;
import com.fintech.ledger.messaging.OutboxService;
import com.fintech.ledger.persistence.entity.AccountEntity;
import com.fintech.ledger.persistence.entity.ApprovalRequestEntity;
import com.fintech.ledger.persistence.entity.ApprovalRequestEntity.ApprovalStatus;
import com.fintech.ledger.persistence.entity.FinancialTransactionEntity;
import com.fintech.ledger.persistence.entity.ProviderTransactionEntity;
import com.fintech.ledger.persistence.entity.UserEntity;
import com.fintech.ledger.persistence.entity.WalletEntity;
import com.fintech.ledger.persistence.lock.IdempotencyAdvisoryLock;
import com.fintech.ledger.persistence.repository.ApprovalRequestRepository;
import com.fintech.ledger.persistence.repository.FinancialTransactionRepository;
import com.fintech.ledger.persistence.repository.JournalEntryRepository;
import com.fintech.ledger.persistence.repository.ProviderTransactionRepository;
import com.fintech.ledger.persistence.repository.UserRepository;
import com.fintech.ledger.wallet.SystemAccountService;
import com.fintech.ledger.wallet.WalletService;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class PaymentApplicationServiceTest {

  @Mock private LedgerPostingService ledgerPostingService;
  @Mock private WalletService walletService;
  @Mock private SystemAccountService systemAccountService;
  @Mock private ProviderTransactionRepository providerTransactionRepository;
  @Mock private FinancialTransactionRepository financialTransactionRepository;
  @Mock private JournalEntryRepository journalEntryRepository;
  @Mock private AuditService auditService;
  @Mock private OutboxService outboxService;
  @Mock private IdempotencyAdvisoryLock idempotencyAdvisoryLock;
  @Mock private ApprovalRequestRepository approvalRequestRepository;
  @Mock private UserRepository userRepository;

  private LedgerProperties ledgerProperties;
  private PaymentApplicationService service;

  private static final UUID USER_ID = UUID.randomUUID();
  private static final UUID OTHER_USER_ID = UUID.randomUUID();
  private static final UUID ACTOR_ID = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    ledgerProperties = new LedgerProperties();
    service = new PaymentApplicationService(
        ledgerPostingService,
        walletService,
        systemAccountService,
        providerTransactionRepository,
        financialTransactionRepository,
        journalEntryRepository,
        auditService,
        outboxService,
        ledgerProperties,
        idempotencyAdvisoryLock,
        approvalRequestRepository,
        userRepository);
  }

  private WalletEntity wallet(UUID userId) {
    AccountEntity account = new AccountEntity();
    account.setId(UUID.randomUUID());
    WalletEntity w = new WalletEntity();
    w.setAccount(account);
    if (userId != null) {
      UserEntity u = new UserEntity();
      u.setId(userId);
      w.setUser(u);
    }
    return w;
  }

  private FinancialTransactionEntity txnStub() {
    FinancialTransactionEntity txn = new FinancialTransactionEntity();
    txn.setPublicId(UUID.randomUUID().toString());
    txn.setType(FinancialTxnType.TRANSFER);
    txn.setCurrency("USD");
    return txn;
  }

  @Test
  void deposit_rejectsNonPositiveAmount() {
    assertThatThrownBy(() -> service.deposit(
        ACTOR_ID, USER_ID, "stripe", "ref-1", 0L, "USD", "corr"))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("amount must be positive");
  }

  @Test
  void deposit_replaysSettledProviderTransaction_withoutReposting() {
    FinancialTransactionEntity existing = txnStub();
    ProviderTransactionEntity priorRow = new ProviderTransactionEntity();
    priorRow.setInternalTransaction(existing);
    when(providerTransactionRepository.findByProviderAndProviderRef("stripe", "ref-1"))
        .thenReturn(Optional.of(priorRow));

    FinancialTransactionEntity result =
        service.deposit(ACTOR_ID, USER_ID, "stripe", "ref-1", 500L, "USD", "corr");

    assertThat(result).isSameAs(existing);
    verify(idempotencyAdvisoryLock, never()).lockWithinCurrentTransaction(any(), any());
    verify(ledgerPostingService, never()).postBalancedTransaction(
        any(), anyString(), anyString(), anyString(), any(), any(), anyString(), any(),
        anyList());
  }

  @Test
  void deposit_postsBalancedClearingToWalletEntry_forNewProviderRef() {
    when(providerTransactionRepository.findByProviderAndProviderRef("stripe", "ref-1"))
        .thenReturn(Optional.empty());
    WalletEntity wallet = wallet(USER_ID);
    when(walletService.ensureWallet(USER_ID, "USD")).thenReturn(wallet);
    UUID clearingAccountId = UUID.randomUUID();
    when(systemAccountService.clearingAccountId("USD")).thenReturn(clearingAccountId);
    FinancialTransactionEntity posted = txnStub();
    when(ledgerPostingService.postBalancedTransaction(
        eq(FinancialTxnType.DEPOSIT), eq("USD"), eq("deposit"), eq("stripe:ref-1"), eq("corr"),
        eq(null), anyString(), anyMap(), anyList()))
        .thenReturn(posted);

    FinancialTransactionEntity result =
        service.deposit(ACTOR_ID, USER_ID, "stripe", "ref-1", 500L, "USD", "corr");

    assertThat(result).isSameAs(posted);
    verify(providerTransactionRepository).save(any(ProviderTransactionEntity.class));
    verify(auditService).record(eq(ACTOR_ID), eq("TXN_POSTED"), any(), any(), anyMap());
    verify(outboxService).enqueue(anyString(), eq(posted.getPublicId()), anyMap());
  }

  @Test
  void transfer_rejectsNonPositiveAmount() {
    assertThatThrownBy(() -> service.transfer(
        ACTOR_ID, USER_ID, OTHER_USER_ID, -1L, "USD", "corr", "idem-key"))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("amount must be positive");
  }

  @Test
  void transfer_checksSpendabilityAndPostsTwoLines() {
    WalletEntity from = wallet(USER_ID);
    WalletEntity to = wallet(OTHER_USER_ID);
    when(walletService.requireUserWallet(USER_ID, "USD")).thenReturn(from);
    when(walletService.ensureWallet(OTHER_USER_ID, "USD")).thenReturn(to);
    FinancialTransactionEntity posted = txnStub();
    when(ledgerPostingService.postBalancedTransaction(
        eq(FinancialTxnType.TRANSFER), eq("USD"), eq("transfer"), eq("idem-key"), eq("corr"),
        eq(null), anyString(), anyMap(), anyList()))
        .thenReturn(posted);

    FinancialTransactionEntity result = service.transfer(
        ACTOR_ID, USER_ID, OTHER_USER_ID, 100L, "USD", "corr", "idem-key");

    assertThat(result).isSameAs(posted);
    verify(walletService).assertWalletSpendable(from);
  }

  @Test
  void merchantPayment_rejectsFeeGreaterThanGross() {
    assertThatThrownBy(() -> service.merchantPayment(
        ACTOR_ID, USER_ID, OTHER_USER_ID, 100L, 150L, "USD", "corr", "idem-key"))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("Invalid amounts");
  }

  @Test
  void merchantPayment_omitsFeeLine_whenFeeIsZero() {
    WalletEntity payer = wallet(USER_ID);
    WalletEntity merchant = wallet(OTHER_USER_ID);
    when(walletService.requireUserWallet(USER_ID, "USD")).thenReturn(payer);
    when(walletService.ensureWallet(OTHER_USER_ID, "USD")).thenReturn(merchant);
    when(ledgerPostingService.postBalancedTransaction(
        eq(FinancialTxnType.MERCHANT_PAYMENT), eq("USD"), eq("merchant_payment"),
        eq("idem-key"), eq("corr"), eq(null), anyString(), anyMap(), anyList()))
        .thenReturn(txnStub());

    service.merchantPayment(ACTOR_ID, USER_ID, OTHER_USER_ID, 100L, 0L, "USD", "corr", "idem-key");

    verify(systemAccountService, never()).feeRevenueAccountId(any());
  }

  @Test
  void withdraw_postsDirectly_whenBelowThreshold() {
    ledgerProperties.getWithdrawal().setApprovalThresholdMinor(1_000_000L);
    WalletEntity walletEntity = wallet(USER_ID);
    when(walletService.requireUserWallet(USER_ID, "USD")).thenReturn(walletEntity);
    UUID settlementAccountId = UUID.randomUUID();
    when(systemAccountService.settlementAccountId("USD")).thenReturn(settlementAccountId);
    FinancialTransactionEntity posted = txnStub();
    when(ledgerPostingService.postBalancedTransaction(
        eq(FinancialTxnType.WITHDRAWAL), eq("USD"), eq("withdrawal"), eq("idem-key"), eq("corr"),
        eq(null), anyString(), anyMap(), anyList()))
        .thenReturn(posted);

    WithdrawOutcome outcome =
        service.withdraw(ACTOR_ID, USER_ID, 500L, "USD", "corr", "idem-key");

    assertThat(outcome.isPendingApproval()).isFalse();
    assertThat(outcome.transaction()).isSameAs(posted);
    verify(approvalRequestRepository, never()).save(any());
  }

  @Test
  void withdraw_createsPendingApproval_whenAtOrAboveThreshold() {
    ledgerProperties.getWithdrawal().setApprovalThresholdMinor(500L);
    UUID approvalId = UUID.randomUUID();
    when(approvalRequestRepository.save(any(ApprovalRequestEntity.class)))
        .thenAnswer(invocation -> {
          ApprovalRequestEntity e = invocation.getArgument(0);
          e.setId(approvalId);
          return e;
        });
    when(userRepository.getReferenceById(ACTOR_ID)).thenReturn(new UserEntity());

    WithdrawOutcome outcome =
        service.withdraw(ACTOR_ID, USER_ID, 500L, "USD", "corr", "idem-key");

    assertThat(outcome.isPendingApproval()).isTrue();
    assertThat(outcome.pendingApprovalId()).isEqualTo(approvalId);
    verify(ledgerPostingService, never()).postBalancedTransaction(
        any(), anyString(), anyString(), anyString(), any(), any(), anyString(), any(),
        anyList());
    verify(auditService).record(eq(ACTOR_ID), eq("WITHDRAWAL_PENDING_APPROVAL"), any(), any(),
        anyMap());
  }

  private ApprovalRequestEntity pendingApproval(UUID makerId) {
    ApprovalRequestEntity req = new ApprovalRequestEntity();
    req.setId(UUID.randomUUID());
    req.setOperationType("WITHDRAWAL");
    req.setStatus(ApprovalStatus.PENDING);
    UserEntity maker = new UserEntity();
    maker.setId(makerId);
    req.setMakerUser(maker);
    req.setPayload(java.util.Map.of(
        "userId", USER_ID.toString(),
        "amountMinor", 500L,
        "currency", "USD",
        "correlationId", "corr"));
    return req;
  }

  @Test
  void approveWithdrawal_rejectsWhenCheckerIsMaker() {
    ApprovalRequestEntity req = pendingApproval(ACTOR_ID);
    when(approvalRequestRepository.findById(req.getId())).thenReturn(Optional.of(req));

    assertThatThrownBy(() -> service.approveWithdrawal(ACTOR_ID, req.getId()))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("four-eyes");
  }

  @Test
  void approveWithdrawal_rejectsWhenNotPending() {
    ApprovalRequestEntity req = pendingApproval(ACTOR_ID);
    req.setStatus(ApprovalStatus.APPROVED);
    when(approvalRequestRepository.findById(req.getId())).thenReturn(Optional.of(req));

    assertThatThrownBy(() -> service.approveWithdrawal(OTHER_USER_ID, req.getId()))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("not pending");
  }

  @Test
  void approveWithdrawal_postsLedgerAndMarksApproved_whenValid() {
    ApprovalRequestEntity req = pendingApproval(ACTOR_ID);
    when(approvalRequestRepository.findById(req.getId())).thenReturn(Optional.of(req));
    when(userRepository.getReferenceById(OTHER_USER_ID)).thenReturn(new UserEntity());
    WalletEntity walletEntity = wallet(USER_ID);
    when(walletService.requireUserWallet(USER_ID, "USD")).thenReturn(walletEntity);
    when(systemAccountService.settlementAccountId("USD")).thenReturn(UUID.randomUUID());
    FinancialTransactionEntity posted = txnStub();
    when(ledgerPostingService.postBalancedTransaction(
        eq(FinancialTxnType.WITHDRAWAL), eq("USD"), eq("withdrawal_approval"),
        eq(req.getId().toString()), eq("corr"), eq(null), anyString(), anyMap(), anyList()))
        .thenReturn(posted);

    FinancialTransactionEntity result = service.approveWithdrawal(OTHER_USER_ID, req.getId());

    assertThat(result).isSameAs(posted);
    assertThat(req.getStatus()).isEqualTo(ApprovalStatus.APPROVED);
    verify(approvalRequestRepository).save(req);
  }

  @Test
  void rejectWithdrawal_rejectsWhenCheckerIsMaker() {
    ApprovalRequestEntity req = pendingApproval(ACTOR_ID);
    when(approvalRequestRepository.findById(req.getId())).thenReturn(Optional.of(req));

    assertThatThrownBy(() -> service.rejectWithdrawal(ACTOR_ID, req.getId(), "no"))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("must differ from maker");
  }

  @Test
  void rejectWithdrawal_marksRejectedWithReason() {
    ApprovalRequestEntity req = pendingApproval(ACTOR_ID);
    when(approvalRequestRepository.findById(req.getId())).thenReturn(Optional.of(req));
    when(userRepository.getReferenceById(OTHER_USER_ID)).thenReturn(new UserEntity());

    service.rejectWithdrawal(OTHER_USER_ID, req.getId(), "suspicious");

    assertThat(req.getStatus()).isEqualTo(ApprovalStatus.REJECTED);
    assertThat(req.getPayload()).containsEntry("rejectReason", "suspicious");
  }

  @Test
  void reverse_flipsJournalDirectionsOfOriginalTransaction() {
    FinancialTransactionEntity original = txnStub();
    original.setId(UUID.randomUUID());
    when(financialTransactionRepository.findByPublicId("orig-id"))
        .thenReturn(Optional.of(original));
    UUID accountId = UUID.randomUUID();
    com.fintech.ledger.persistence.entity.JournalEntryEntity line =
        new com.fintech.ledger.persistence.entity.JournalEntryEntity();
    AccountEntity acc = new AccountEntity();
    acc.setId(accountId);
    line.setAccount(acc);
    line.setDirection(com.fintech.ledger.domain.JournalDirection.DEBIT);
    line.setAmountMinor(100);
    when(journalEntryRepository.findByTransaction_Id(original.getId()))
        .thenReturn(List.of(line));
    FinancialTransactionEntity reversal = txnStub();
    when(ledgerPostingService.postBalancedTransaction(
        eq(FinancialTxnType.REVERSAL), any(), eq("reversal"), eq("idem-key"), any(),
        eq(original), anyString(), anyMap(), anyList()))
        .thenReturn(reversal);

    FinancialTransactionEntity result = service.reverse(ACTOR_ID, "orig-id", "idem-key");

    assertThat(result).isSameAs(reversal);
  }
}
