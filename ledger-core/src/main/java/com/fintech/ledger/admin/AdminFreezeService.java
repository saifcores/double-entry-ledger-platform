package com.fintech.ledger.admin;

import com.fintech.ledger.audit.AuditService;
import com.fintech.ledger.persistence.entity.AccountEntity;
import com.fintech.ledger.persistence.entity.WalletEntity;
import com.fintech.ledger.persistence.repository.AccountRepository;
import com.fintech.ledger.persistence.repository.WalletRepository;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class AdminFreezeService {

  private final WalletRepository walletRepository;
  private final AccountRepository accountRepository;
  private final AuditService auditService;

  @Transactional
  public void freezeWallet(UUID actorUserId, UUID walletId) {
    WalletEntity w =
        walletRepository
            .findByIdForUpdate(walletId)
            .orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Wallet not found"));
    w.setFrozen(true);
    walletRepository.save(w);
    AccountEntity acc =
        accountRepository
            .findByIdForUpdate(w.getAccount().getId())
            .orElseThrow();
    acc.setFrozen(true);
    accountRepository.save(acc);
    auditService.record(
        actorUserId,
        "WALLET_FROZEN",
        "WALLET",
        walletId.toString(),
        Map.of("accountId", acc.getId().toString()));
  }

  @Transactional
  public void unfreezeWallet(UUID actorUserId, UUID walletId) {
    WalletEntity w =
        walletRepository
            .findByIdForUpdate(walletId)
            .orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Wallet not found"));
    w.setFrozen(false);
    walletRepository.save(w);
    AccountEntity acc =
        accountRepository
            .findByIdForUpdate(w.getAccount().getId())
            .orElseThrow();
    acc.setFrozen(false);
    accountRepository.save(acc);
    auditService.record(
        actorUserId,
        "WALLET_UNFROZEN",
        "WALLET",
        walletId.toString(),
        Map.of("accountId", acc.getId().toString()));
  }

  @Transactional
  public void freezeAccount(UUID actorUserId, UUID accountId) {
    AccountEntity acc =
        accountRepository
            .findByIdForUpdate(accountId)
            .orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found"));
    acc.setFrozen(true);
    accountRepository.save(acc);
    auditService.record(
        actorUserId,
        "ACCOUNT_FROZEN",
        "ACCOUNT",
        accountId.toString(),
        Map.of("code", acc.getCode()));
  }

  @Transactional
  public void unfreezeAccount(UUID actorUserId, UUID accountId) {
    AccountEntity acc =
        accountRepository
            .findByIdForUpdate(accountId)
            .orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found"));
    acc.setFrozen(false);
    accountRepository.save(acc);
    auditService.record(
        actorUserId,
        "ACCOUNT_UNFROZEN",
        "ACCOUNT",
        accountId.toString(),
        Map.of("code", acc.getCode()));
  }
}
