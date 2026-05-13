package com.fintech.ledger.ledger;

import com.fintech.ledger.domain.AccountType;
import com.fintech.ledger.domain.FinancialTxnStatus;
import com.fintech.ledger.domain.FinancialTxnType;
import com.fintech.ledger.domain.JournalDirection;
import com.fintech.ledger.persistence.entity.AccountEntity;
import com.fintech.ledger.persistence.entity.FinancialTransactionEntity;
import com.fintech.ledger.persistence.entity.JournalEntryEntity;
import com.fintech.ledger.persistence.lock.IdempotencyAdvisoryLock;
import com.fintech.ledger.persistence.repository.AccountRepository;
import com.fintech.ledger.persistence.repository.FinancialTransactionRepository;
import com.fintech.ledger.persistence.repository.JournalEntryRepository;
import com.fintech.ledger.persistence.repository.WalletRepository;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Slf4j
@Service
@RequiredArgsConstructor
public class LedgerPostingService {

  private final FinancialTransactionRepository financialTransactionRepository;
  private final JournalEntryRepository journalEntryRepository;
  private final AccountRepository accountRepository;
  private final WalletRepository walletRepository;
  private final IdempotencyAdvisoryLock idempotencyAdvisoryLock;

  @Transactional
  public FinancialTransactionEntity postBalancedTransaction(
      FinancialTxnType type,
      String currency,
      String idempotencyScope,
      String idempotencyKey,
      String correlationId,
      FinancialTransactionEntity relatedTransaction,
      String description,
      Map<String, Object> metadata,
      List<PostingLine> lines) {
    Optional<FinancialTransactionEntity> existing = financialTransactionRepository
        .findByIdempotencyScopeAndIdempotencyKey(
            idempotencyScope, idempotencyKey);
    if (existing.isPresent()) {
      log.info("Idempotent replay txn publicId={}", existing.get().getPublicId());
      return existing.get();
    }

    idempotencyAdvisoryLock.lockWithinCurrentTransaction(idempotencyScope, idempotencyKey);
    Optional<FinancialTransactionEntity> afterLock = financialTransactionRepository
        .findByIdempotencyScopeAndIdempotencyKey(
            idempotencyScope, idempotencyKey);
    if (afterLock.isPresent()) {
      log.info(
          "Idempotent after advisory lock publicId={}", afterLock.get().getPublicId());
      return afterLock.get();
    }

    if (lines == null || lines.size() < 2) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "At least two journal lines required");
    }
    long debitSum = lines.stream()
        .filter(l -> l.direction() == JournalDirection.DEBIT)
        .mapToLong(PostingLine::amountMinor)
        .sum();
    long creditSum = lines.stream()
        .filter(l -> l.direction() == JournalDirection.CREDIT)
        .mapToLong(PostingLine::amountMinor)
        .sum();
    if (debitSum != creditSum) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Unbalanced journal: debits != credits");
    }

    Set<UUID> accountIds = lines.stream().map(PostingLine::accountId).collect(Collectors.toCollection(TreeSet::new));
    List<UUID> sortedIds = new ArrayList<>(accountIds);
    sortedIds.sort(Comparator.naturalOrder());
    List<AccountEntity> locked = new ArrayList<>();
    for (UUID id : sortedIds) {
      AccountEntity a = accountRepository
          .findByIdForUpdate(id)
          .orElseThrow(
              () -> new ResponseStatusException(
                  HttpStatus.NOT_FOUND, "Account not found: " + id));
      if (a.isFrozen()) {
        throw new ResponseStatusException(
            HttpStatus.CONFLICT, "Account frozen: " + a.getCode());
      }
      if (!currency.equals(a.getCurrency())) {
        throw new ResponseStatusException(
            HttpStatus.BAD_REQUEST,
            "Currency mismatch on account " + a.getId() + " expected " + currency);
      }
      locked.add(a);
    }
    Map<UUID, AccountEntity> byId = locked.stream().collect(Collectors.toMap(AccountEntity::getId, a -> a));

    Map<UUID, Long> simulated = new HashMap<>();
    for (AccountEntity a : locked) {
      simulated.put(a.getId(), a.getBalanceMinor());
    }
    for (PostingLine line : lines) {
      AccountEntity acc = byId.get(line.accountId());
      long next = simulated.get(acc.getId())
          + BalanceMath.signedDelta(acc.getType(), line.direction(), line.amountMinor());
      simulated.put(acc.getId(), next);
    }
    for (AccountEntity acc : locked) {
      long next = simulated.get(acc.getId());
      if (acc.getType() == AccountType.LIABILITY) {
        boolean userWallet = walletRepository
            .findByAccountId(acc.getId())
            .map(w -> w.getUser() != null)
            .orElse(false);
        if (userWallet && next < 0) {
          throw new ResponseStatusException(HttpStatus.CONFLICT, "Insufficient wallet balance");
        }
      }
      if ((acc.getType() == AccountType.ASSET || acc.getType() == AccountType.EXPENSE)
          && next < 0) {
        throw new ResponseStatusException(
            HttpStatus.CONFLICT, "Account balance would go negative: " + acc.getCode());
      }
    }

    FinancialTransactionEntity txn = new FinancialTransactionEntity();
    txn.setPublicId(UUID.randomUUID().toString());
    txn.setType(type);
    txn.setStatus(FinancialTxnStatus.POSTED);
    txn.setCurrency(currency);
    txn.setIdempotencyKey(idempotencyKey);
    txn.setIdempotencyScope(idempotencyScope);
    txn.setCorrelationId(correlationId);
    txn.setRelatedTransaction(relatedTransaction);
    txn.setDescription(description);
    if (metadata != null) {
      txn.setMetadata(metadata);
    }
    financialTransactionRepository.save(txn);

    for (PostingLine line : lines) {
      JournalEntryEntity je = new JournalEntryEntity();
      je.setTransaction(txn);
      je.setAccount(byId.get(line.accountId()));
      je.setDirection(line.direction());
      je.setAmountMinor(line.amountMinor());
      je.setCurrency(currency);
      je.setMemo(line.memo());
      journalEntryRepository.save(je);
    }

    for (AccountEntity acc : locked) {
      acc.setBalanceMinor(simulated.get(acc.getId()));
    }
    accountRepository.saveAll(locked);

    log.info("Posted txn publicId={} type={}", txn.getPublicId(), type);
    return txn;
  }
}
