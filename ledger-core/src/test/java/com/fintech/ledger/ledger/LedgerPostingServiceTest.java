package com.fintech.ledger.ledger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fintech.ledger.domain.AccountType;
import com.fintech.ledger.domain.FinancialTxnStatus;
import com.fintech.ledger.domain.FinancialTxnType;
import com.fintech.ledger.domain.JournalDirection;
import com.fintech.ledger.persistence.entity.AccountEntity;
import com.fintech.ledger.persistence.entity.FinancialTransactionEntity;
import com.fintech.ledger.persistence.entity.WalletEntity;
import com.fintech.ledger.persistence.lock.IdempotencyAdvisoryLock;
import com.fintech.ledger.persistence.repository.AccountRepository;
import com.fintech.ledger.persistence.repository.FinancialTransactionRepository;
import com.fintech.ledger.persistence.repository.JournalEntryRepository;
import com.fintech.ledger.persistence.repository.WalletRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class LedgerPostingServiceTest {

  private static final UUID ACCOUNT_A = UUID.fromString("00000000-0000-0000-0000-000000000001");
  private static final UUID ACCOUNT_B = UUID.fromString("00000000-0000-0000-0000-000000000002");

  @Mock private FinancialTransactionRepository financialTransactionRepository;
  @Mock private JournalEntryRepository journalEntryRepository;
  @Mock private AccountRepository accountRepository;
  @Mock private WalletRepository walletRepository;
  @Mock private IdempotencyAdvisoryLock idempotencyAdvisoryLock;

  private LedgerPostingService service;

  @BeforeEach
  void setUp() {
    service = new LedgerPostingService(
        financialTransactionRepository,
        journalEntryRepository,
        accountRepository,
        walletRepository,
        idempotencyAdvisoryLock);
  }

  private AccountEntity account(UUID id, AccountType type, String currency, long balance) {
    AccountEntity a = new AccountEntity();
    a.setId(id);
    a.setCode("ACC_" + id);
    a.setType(type);
    a.setCurrency(currency);
    a.setFrozen(false);
    a.setBalanceMinor(balance);
    return a;
  }

  @Test
  void replaysExistingTransaction_whenIdempotencyKeyFoundBeforeLock() {
    FinancialTransactionEntity existing = new FinancialTransactionEntity();
    existing.setPublicId("existing-txn");
    when(financialTransactionRepository.findByIdempotencyScopeAndIdempotencyKey("scope", "key"))
        .thenReturn(Optional.of(existing));

    FinancialTransactionEntity result = service.postBalancedTransaction(
        FinancialTxnType.TRANSFER, "USD", "scope", "key", null, null, "desc", null,
        List.of());

    assertThat(result).isSameAs(existing);
    verify(idempotencyAdvisoryLock, never()).lockWithinCurrentTransaction(any(), any());
    verify(financialTransactionRepository, never()).save(any());
  }

  @Test
  void replaysExistingTransaction_whenFoundOnlyAfterAdvisoryLock() {
    FinancialTransactionEntity existing = new FinancialTransactionEntity();
    existing.setPublicId("existing-txn");
    when(financialTransactionRepository.findByIdempotencyScopeAndIdempotencyKey("scope", "key"))
        .thenReturn(Optional.empty())
        .thenReturn(Optional.of(existing));

    FinancialTransactionEntity result = service.postBalancedTransaction(
        FinancialTxnType.TRANSFER, "USD", "scope", "key", null, null, "desc", null,
        List.of());

    assertThat(result).isSameAs(existing);
    verify(idempotencyAdvisoryLock).lockWithinCurrentTransaction("scope", "key");
    verify(financialTransactionRepository, never()).save(any());
  }

  @Test
  void rejectsFewerThanTwoLines() {
    when(financialTransactionRepository.findByIdempotencyScopeAndIdempotencyKey(any(), any()))
        .thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.postBalancedTransaction(
        FinancialTxnType.TRANSFER, "USD", "scope", "key", null, null, "desc", null,
        List.of(new PostingLine(ACCOUNT_A, JournalDirection.DEBIT, 100, "only one"))))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("At least two journal lines");
  }

  @Test
  void rejectsUnbalancedJournal() {
    when(financialTransactionRepository.findByIdempotencyScopeAndIdempotencyKey(any(), any()))
        .thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.postBalancedTransaction(
        FinancialTxnType.TRANSFER, "USD", "scope", "key", null, null, "desc", null,
        List.of(
            new PostingLine(ACCOUNT_A, JournalDirection.DEBIT, 100, "debit"),
            new PostingLine(ACCOUNT_B, JournalDirection.CREDIT, 90, "credit"))))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("Unbalanced journal");
  }

  @Test
  void rejectsWhenAccountFrozen() {
    when(financialTransactionRepository.findByIdempotencyScopeAndIdempotencyKey(any(), any()))
        .thenReturn(Optional.empty());
    AccountEntity frozen = account(ACCOUNT_A, AccountType.ASSET, "USD", 1000);
    frozen.setFrozen(true);
    when(accountRepository.findByIdForUpdate(ACCOUNT_A)).thenReturn(Optional.of(frozen));

    assertThatThrownBy(() -> service.postBalancedTransaction(
        FinancialTxnType.TRANSFER, "USD", "scope", "key", null, null, "desc", null,
        List.of(
            new PostingLine(ACCOUNT_A, JournalDirection.CREDIT, 100, "credit"),
            new PostingLine(ACCOUNT_B, JournalDirection.DEBIT, 100, "debit"))))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("frozen");
  }

  @Test
  void rejectsCurrencyMismatch() {
    when(financialTransactionRepository.findByIdempotencyScopeAndIdempotencyKey(any(), any()))
        .thenReturn(Optional.empty());
    when(accountRepository.findByIdForUpdate(ACCOUNT_A))
        .thenReturn(Optional.of(account(ACCOUNT_A, AccountType.ASSET, "EUR", 1000)));

    assertThatThrownBy(() -> service.postBalancedTransaction(
        FinancialTxnType.TRANSFER, "USD", "scope", "key", null, null, "desc", null,
        List.of(
            new PostingLine(ACCOUNT_A, JournalDirection.CREDIT, 100, "credit"),
            new PostingLine(ACCOUNT_B, JournalDirection.DEBIT, 100, "debit"))))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("Currency mismatch");
  }

  @Test
  void rejectsWhenUserWalletWouldGoNegative() {
    when(financialTransactionRepository.findByIdempotencyScopeAndIdempotencyKey(any(), any()))
        .thenReturn(Optional.empty());
    AccountEntity asset = account(ACCOUNT_A, AccountType.ASSET, "USD", 1000);
    AccountEntity liability = account(ACCOUNT_B, AccountType.LIABILITY, "USD", 50);
    when(accountRepository.findByIdForUpdate(ACCOUNT_A)).thenReturn(Optional.of(asset));
    when(accountRepository.findByIdForUpdate(ACCOUNT_B)).thenReturn(Optional.of(liability));
    WalletEntity userWallet = new WalletEntity();
    userWallet.setUser(new com.fintech.ledger.persistence.entity.UserEntity());
    when(walletRepository.findByAccountId(ACCOUNT_B)).thenReturn(Optional.of(userWallet));

    assertThatThrownBy(() -> service.postBalancedTransaction(
        FinancialTxnType.WITHDRAWAL, "USD", "scope", "key", null, null, "desc", null,
        List.of(
            new PostingLine(ACCOUNT_B, JournalDirection.DEBIT, 100, "wallet debit"),
            new PostingLine(ACCOUNT_A, JournalDirection.CREDIT, 100, "asset credit"))))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("Insufficient wallet balance");
  }

  @Test
  void rejectsWhenAssetAccountWouldGoNegative() {
    when(financialTransactionRepository.findByIdempotencyScopeAndIdempotencyKey(any(), any()))
        .thenReturn(Optional.empty());
    AccountEntity asset = account(ACCOUNT_A, AccountType.ASSET, "USD", 50);
    AccountEntity liability = account(ACCOUNT_B, AccountType.LIABILITY, "USD", 1000);
    when(accountRepository.findByIdForUpdate(ACCOUNT_A)).thenReturn(Optional.of(asset));
    when(accountRepository.findByIdForUpdate(ACCOUNT_B)).thenReturn(Optional.of(liability));

    assertThatThrownBy(() -> service.postBalancedTransaction(
        FinancialTxnType.DEPOSIT, "USD", "scope", "key", null, null, "desc", null,
        List.of(
            new PostingLine(ACCOUNT_A, JournalDirection.CREDIT, 100, "asset credit"),
            new PostingLine(ACCOUNT_B, JournalDirection.DEBIT, 100, "wallet debit"))))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("would go negative");
  }

  @Test
  void postsBalancedTransactionAndUpdatesBalancesInDeterministicLockOrder() {
    when(financialTransactionRepository.findByIdempotencyScopeAndIdempotencyKey(any(), any()))
        .thenReturn(Optional.empty());
    AccountEntity asset = account(ACCOUNT_A, AccountType.ASSET, "USD", 1000);
    AccountEntity liability = account(ACCOUNT_B, AccountType.LIABILITY, "USD", 0);
    when(accountRepository.findByIdForUpdate(ACCOUNT_A)).thenReturn(Optional.of(asset));
    when(accountRepository.findByIdForUpdate(ACCOUNT_B)).thenReturn(Optional.of(liability));
    when(walletRepository.findByAccountId(ACCOUNT_B)).thenReturn(Optional.empty());

    FinancialTransactionEntity txn = service.postBalancedTransaction(
        FinancialTxnType.DEPOSIT, "USD", "scope", "key", "corr", null, "desc", null,
        List.of(
            new PostingLine(ACCOUNT_A, JournalDirection.DEBIT, 100, "clearing debit"),
            new PostingLine(ACCOUNT_B, JournalDirection.CREDIT, 100, "wallet credit")));

    assertThat(txn.getStatus()).isEqualTo(FinancialTxnStatus.POSTED);
    assertThat(txn.getCurrency()).isEqualTo("USD");
    assertThat(asset.getBalanceMinor()).isEqualTo(1100);
    assertThat(liability.getBalanceMinor()).isEqualTo(100);

    InOrder inOrder = Mockito.inOrder(accountRepository);
    inOrder.verify(accountRepository).findByIdForUpdate(ACCOUNT_A);
    inOrder.verify(accountRepository).findByIdForUpdate(ACCOUNT_B);

    verify(journalEntryRepository, times(2)).save(any());
    verify(accountRepository).saveAll(List.of(asset, liability));
    verify(financialTransactionRepository).save(eq(txn));
  }
}
