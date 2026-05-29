package com.fintech.ledger.admin;

import com.fintech.ledger.audit.AuditService;
import com.fintech.ledger.persistence.entity.FraudFlagEntity;
import com.fintech.ledger.persistence.entity.WalletEntity;
import com.fintech.ledger.persistence.repository.FraudFlagRepository;
import com.fintech.ledger.persistence.repository.WalletRepository;
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
public class FraudFlagService {

  private static final String SUBJECT_WALLET = "WALLET";

  private final WalletRepository walletRepository;
  private final FraudFlagRepository fraudFlagRepository;
  private final AuditService auditService;

  @Transactional
  public FraudFlagView flagWallet(UUID actorUserId, UUID walletId, String reason, String severity) {
    WalletEntity wallet =
        walletRepository
            .findByIdForUpdate(walletId)
            .orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Wallet not found"));
    FraudFlagEntity flag = new FraudFlagEntity();
    flag.setSubjectType(SUBJECT_WALLET);
    flag.setSubjectId(walletId);
    flag.setReason(reason != null && !reason.isBlank() ? reason : "Fraud review");
    flag.setSeverity(severity != null && !severity.isBlank() ? severity : "MEDIUM");
    flag.setActive(true);
    fraudFlagRepository.save(flag);
    wallet.setFraudLocked(true);
    walletRepository.save(wallet);
    auditService.record(
        actorUserId,
        "WALLET_FRAUD_FLAGGED",
        "WALLET",
        walletId.toString(),
        Map.of("flagId", flag.getId(), "reason", flag.getReason()));
    return toView(flag);
  }

  @Transactional
  public void clearWalletFlags(UUID actorUserId, UUID walletId) {
    WalletEntity wallet =
        walletRepository
            .findByIdForUpdate(walletId)
            .orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Wallet not found"));
    List<FraudFlagEntity> flags =
        fraudFlagRepository.findBySubjectTypeAndSubjectIdOrderByCreatedAtDesc(
            SUBJECT_WALLET, walletId);
    for (FraudFlagEntity flag : flags) {
      if (flag.isActive()) {
        flag.setActive(false);
      }
    }
    fraudFlagRepository.saveAll(flags);
    wallet.setFraudLocked(false);
    walletRepository.save(wallet);
    auditService.record(
        actorUserId,
        "WALLET_FRAUD_CLEARED",
        "WALLET",
        walletId.toString(),
        Map.of("clearedCount", flags.size()));
  }

  @Transactional(readOnly = true)
  public List<FraudFlagView> listWalletFlags(UUID walletId) {
    return fraudFlagRepository
        .findBySubjectTypeAndSubjectIdOrderByCreatedAtDesc(SUBJECT_WALLET, walletId)
        .stream()
        .map(this::toView)
        .toList();
  }

  private FraudFlagView toView(FraudFlagEntity flag) {
    return new FraudFlagView(
        flag.getId(),
        flag.getSubjectType(),
        flag.getSubjectId(),
        flag.getReason(),
        flag.getSeverity(),
        flag.isActive(),
        flag.getCreatedAt());
  }

  public record FraudFlagView(
      UUID id,
      String subjectType,
      UUID subjectId,
      String reason,
      String severity,
      boolean active,
      java.time.Instant createdAt) {}
}
