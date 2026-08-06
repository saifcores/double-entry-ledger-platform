package com.fintech.ledger.wallet;

import com.fintech.ledger.domain.AccountType;
import com.fintech.ledger.persistence.entity.AccountEntity;
import com.fintech.ledger.persistence.entity.UserEntity;
import com.fintech.ledger.persistence.entity.WalletEntity;
import com.fintech.ledger.persistence.repository.AccountRepository;
import com.fintech.ledger.persistence.repository.FraudFlagRepository;
import com.fintech.ledger.persistence.repository.UserRepository;
import com.fintech.ledger.persistence.repository.WalletRepository;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class WalletService {

  private final WalletRepository walletRepository;
  private final AccountRepository accountRepository;
  private final UserRepository userRepository;
  private final FraudFlagRepository fraudFlagRepository;

  @Transactional
  public WalletEntity ensureWallet(UUID userId, String currency) {
    String ccy = currency.toUpperCase();
    Optional<WalletEntity> existing = walletRepository.findByUser_IdAndCurrency(userId, ccy);
    if (existing.isPresent()) {
      return existing.get();
    }
    try {
      return provisionWallet(userId, ccy);
    } catch (DataIntegrityViolationException e) {
      // Concurrent request already provisioned this user/currency wallet under the
      // uq_wallets_user_currency constraint; fall back to the row it created.
      return walletRepository
          .findByUser_IdAndCurrency(userId, ccy)
          .orElseThrow(() -> e);
    }
  }

  @Transactional(readOnly = true)
  public WalletEntity requireUserWallet(UUID userId, String currency) {
    String ccy = currency.toUpperCase();
    return walletRepository
        .findByUser_IdAndCurrency(userId, ccy)
        .orElseThrow(
            () -> new ResponseStatusException(
                HttpStatus.NOT_FOUND, "Wallet not found for currency " + ccy));
  }

  // Not @Transactional: only ever self-invoked from ensureWallet, which already runs
  // inside a transaction. A Spring proxy never intercepts a self-invocation, so an
  // annotation here would have no effect and could mislead readers into thinking it does.
  private WalletEntity provisionWallet(UUID userId, String currency) {
    UserEntity user = userRepository
        .findById(userId)
        .orElseThrow(
            () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
    if (user.isFrozen() || user.getStatus() != UserEntity.UserStatus.ACTIVE) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "User inactive or frozen");
    }
    AccountEntity account = new AccountEntity();
    account.setCode("UW_" + userId + "_" + currency);
    account.setName("User wallet " + userId + " " + currency);
    account.setType(AccountType.LIABILITY);
    account.setCurrency(currency);
    account.setFrozen(false);
    account.setBalanceMinor(0L);
    accountRepository.save(account);

    WalletEntity wallet = new WalletEntity();
    wallet.setUser(user);
    wallet.setAccount(account);
    wallet.setCurrency(currency);
    wallet.setLabel("Primary " + currency + " wallet");
    return walletRepository.save(wallet);
  }

  @Transactional
  public void assertWalletSpendable(WalletEntity wallet) {
    WalletEntity locked = walletRepository
        .findByIdForUpdate(wallet.getId())
        .orElseThrow(
            () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Wallet missing"));
    if (locked.isFrozen() || locked.isFraudLocked()) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Wallet locked");
    }
    if (fraudFlagRepository.existsBySubjectTypeAndSubjectIdAndActiveTrue(
        "WALLET", locked.getId())) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Wallet fraud flagged");
    }
    if (locked.getUser() != null && locked.getUser().isFrozen()) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "User frozen");
    }
  }
}
