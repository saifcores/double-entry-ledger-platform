package com.fintech.ledger.transaction;

import com.fintech.ledger.audit.AuditService;
import com.fintech.ledger.config.LedgerProperties;
import com.fintech.ledger.domain.FinancialTxnType;
import com.fintech.ledger.domain.JournalDirection;
import com.fintech.ledger.ledger.LedgerPostingService;
import com.fintech.ledger.ledger.PostingLine;
import com.fintech.ledger.messaging.OutboxService;
import com.fintech.ledger.persistence.entity.ApprovalRequestEntity;
import com.fintech.ledger.persistence.entity.ApprovalRequestEntity.ApprovalStatus;
import com.fintech.ledger.persistence.entity.FinancialTransactionEntity;
import com.fintech.ledger.persistence.entity.JournalEntryEntity;
import com.fintech.ledger.persistence.entity.ProviderTransactionEntity;
import com.fintech.ledger.persistence.entity.WalletEntity;
import com.fintech.ledger.persistence.lock.IdempotencyAdvisoryLock;
import com.fintech.ledger.persistence.repository.ApprovalRequestRepository;
import com.fintech.ledger.persistence.repository.FinancialTransactionRepository;
import com.fintech.ledger.persistence.repository.JournalEntryRepository;
import com.fintech.ledger.persistence.repository.ProviderTransactionRepository;
import com.fintech.ledger.persistence.repository.UserRepository;
import com.fintech.ledger.wallet.SystemAccountService;
import com.fintech.ledger.wallet.WalletService;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentApplicationService {

  private final LedgerPostingService ledgerPostingService;
  private final WalletService walletService;
  private final SystemAccountService systemAccountService;
  private final ProviderTransactionRepository providerTransactionRepository;
  private final FinancialTransactionRepository financialTransactionRepository;
  private final JournalEntryRepository journalEntryRepository;
  private final AuditService auditService;
  private final OutboxService outboxService;
  private final LedgerProperties ledgerProperties;
  private final IdempotencyAdvisoryLock idempotencyAdvisoryLock;
  private final ApprovalRequestRepository approvalRequestRepository;
  private final UserRepository userRepository;

  @Transactional
  public FinancialTransactionEntity deposit(
      UUID actorUserId,
      UUID beneficiaryUserId,
      String provider,
      String providerRef,
      long amountMinor,
      String currency,
      String correlationId) {
    if (amountMinor <= 0) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "amount must be positive");
    }
    Optional<ProviderTransactionEntity> prior =
        providerTransactionRepository.findByProviderAndProviderRef(provider, providerRef);
    if (prior.map(ProviderTransactionEntity::getInternalTransaction).isPresent()) {
      return prior.get().getInternalTransaction();
    }

    idempotencyAdvisoryLock.lockWithinCurrentTransaction(
        "provider_row", provider + ":" + providerRef);
    prior = providerTransactionRepository.findByProviderAndProviderRef(provider, providerRef);
    if (prior.map(ProviderTransactionEntity::getInternalTransaction).isPresent()) {
      return prior.get().getInternalTransaction();
    }

    String ccy = currency.toUpperCase();
    WalletEntity wallet = walletService.ensureWallet(beneficiaryUserId, ccy);
    String idempotencyScope = "deposit";
    String idempotencyKey = provider + ":" + providerRef;
    FinancialTransactionEntity txn =
        ledgerPostingService.postBalancedTransaction(
            FinancialTxnType.DEPOSIT,
            ccy,
            idempotencyScope,
            idempotencyKey,
            correlationId,
            null,
            "Provider deposit " + providerRef,
            Map.of("provider", provider, "providerRef", providerRef, "userId", beneficiaryUserId),
            List.of(
                new PostingLine(
                    systemAccountService.clearingAccountId(ccy),
                    JournalDirection.DEBIT,
                    amountMinor,
                    "Incoming provider funds"),
                new PostingLine(
                    wallet.getAccount().getId(),
                    JournalDirection.CREDIT,
                    amountMinor,
                    "Wallet credit")));
    ProviderTransactionEntity providerRow = prior.orElseGet(ProviderTransactionEntity::new);
    providerRow.setProvider(provider);
    providerRow.setProviderRef(providerRef);
    providerRow.setStatus("SETTLED");
    providerRow.setAmountMinor(amountMinor);
    providerRow.setCurrency(ccy);
    providerRow.setInternalTransaction(txn);
    providerTransactionRepository.save(providerRow);

    afterPosted(actorUserId, txn);
    return txn;
  }

  @Transactional
  public FinancialTransactionEntity transfer(
      UUID actorUserId,
      UUID fromUserId,
      UUID toUserId,
      long amountMinor,
      String currency,
      String correlationId,
      String clientIdempotencyKey) {
    if (amountMinor <= 0) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "amount must be positive");
    }
    String ccy = currency.toUpperCase();
    WalletEntity from = walletService.requireUserWallet(fromUserId, ccy);
    WalletEntity to = walletService.ensureWallet(toUserId, ccy);
    walletService.assertWalletSpendable(from);
    FinancialTransactionEntity txn =
        ledgerPostingService.postBalancedTransaction(
            FinancialTxnType.TRANSFER,
            ccy,
            "transfer",
            clientIdempotencyKey,
            correlationId,
            null,
            "P2P transfer",
            Map.of("from", fromUserId, "to", toUserId),
            List.of(
                new PostingLine(
                    from.getAccount().getId(),
                    JournalDirection.DEBIT,
                    amountMinor,
                    "Sender debit"),
                new PostingLine(
                    to.getAccount().getId(),
                    JournalDirection.CREDIT,
                    amountMinor,
                    "Receiver credit")));
    afterPosted(actorUserId, txn);
    return txn;
  }

  @Transactional
  public FinancialTransactionEntity merchantPayment(
      UUID actorUserId,
      UUID payerUserId,
      UUID merchantUserId,
      long grossMinor,
      long feeMinor,
      String currency,
      String correlationId,
      String clientIdempotencyKey) {
    if (grossMinor <= 0 || feeMinor < 0 || feeMinor > grossMinor) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid amounts");
    }
    String ccy = currency.toUpperCase();
    long net = grossMinor - feeMinor;
    WalletEntity payer = walletService.requireUserWallet(payerUserId, ccy);
    WalletEntity merchant = walletService.ensureWallet(merchantUserId, ccy);
    walletService.assertWalletSpendable(payer);
    List<PostingLine> lines =
        new java.util.ArrayList<>(
            List.of(
                new PostingLine(
                    payer.getAccount().getId(),
                    JournalDirection.DEBIT,
                    grossMinor,
                    "Customer charge")));
    lines.add(
        new PostingLine(
            merchant.getAccount().getId(),
            JournalDirection.CREDIT,
            net,
            "Merchant payout"));
    if (feeMinor > 0) {
      lines.add(
          new PostingLine(
              systemAccountService.feeRevenueAccountId(ccy),
              JournalDirection.CREDIT,
              feeMinor,
              "Platform fee"));
    }
    FinancialTransactionEntity txn =
        ledgerPostingService.postBalancedTransaction(
            FinancialTxnType.MERCHANT_PAYMENT,
            ccy,
            "merchant_payment",
            clientIdempotencyKey,
            correlationId,
            null,
            "Merchant checkout",
            Map.of(
                "payer",
                payerUserId,
                "merchant",
                merchantUserId,
                "feeMinor",
                feeMinor),
            lines);
    afterPosted(actorUserId, txn);
    return txn;
  }

  @Transactional
  public WithdrawOutcome withdraw(
      UUID actorUserId,
      UUID userId,
      long amountMinor,
      String currency,
      String correlationId,
      String clientIdempotencyKey) {
    if (amountMinor <= 0) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "amount must be positive");
    }
    long threshold = ledgerProperties.getWithdrawal().getApprovalThresholdMinor();
    if (amountMinor >= threshold) {
      UUID approvalId =
          createWithdrawalApproval(
              actorUserId,
              userId,
              amountMinor,
              currency,
              correlationId,
              clientIdempotencyKey);
      auditService.record(
          actorUserId,
          "WITHDRAWAL_PENDING_APPROVAL",
          "APPROVAL_REQUEST",
          approvalId.toString(),
          Map.of("amountMinor", amountMinor, "currency", currency.toUpperCase()));
      return WithdrawOutcome.pendingApproval(approvalId);
    }
    return WithdrawOutcome.posted(
        postWithdrawalLedger(
            actorUserId, userId, amountMinor, currency, correlationId, clientIdempotencyKey));
  }

  @Transactional
  public FinancialTransactionEntity approveWithdrawal(UUID checkerUserId, UUID approvalRequestId) {
    ApprovalRequestEntity req =
        approvalRequestRepository
            .findById(approvalRequestId)
            .orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Approval not found"));
    if (req.getStatus() != ApprovalStatus.PENDING) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Approval not pending");
    }
    if (req.getMakerUser().getId().equals(checkerUserId)) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "Checker must differ from maker (four-eyes)");
    }
    if (!"WITHDRAWAL".equals(req.getOperationType())) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported approval type");
    }
    Map<String, Object> payload = req.getPayload();
    UUID userId = UUID.fromString((String) payload.get("userId"));
    long amountMinor = ((Number) payload.get("amountMinor")).longValue();
    String ccy = (String) payload.get("currency");
    String correlationIdRaw = (String) payload.get("correlationId");
    String correlationId =
        correlationIdRaw != null && !correlationIdRaw.isBlank()
            ? correlationIdRaw
            : UUID.randomUUID().toString();

    String idemScope = "withdrawal_approval";
    String idemKey = approvalRequestId.toString();

    FinancialTransactionEntity txn =
        postWithdrawalLedger(
            checkerUserId, userId, amountMinor, ccy, correlationId, idemScope, idemKey);

    req.setStatus(ApprovalStatus.APPROVED);
    req.setCheckerUser(userRepository.getReferenceById(checkerUserId));
    req.setDecidedAt(Instant.now());
    approvalRequestRepository.save(req);

    auditService.record(
        checkerUserId,
        "WITHDRAWAL_APPROVED",
        "APPROVAL_REQUEST",
        approvalRequestId.toString(),
        Map.of("txnPublicId", txn.getPublicId()));
    return txn;
  }

  @Transactional
  public void rejectWithdrawal(UUID checkerUserId, UUID approvalRequestId, String reason) {
    ApprovalRequestEntity req =
        approvalRequestRepository
            .findById(approvalRequestId)
            .orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Approval not found"));
    if (req.getStatus() != ApprovalStatus.PENDING) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Approval not pending");
    }
    if (req.getMakerUser().getId().equals(checkerUserId)) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Checker must differ from maker");
    }
    req.setStatus(ApprovalStatus.REJECTED);
    req.setCheckerUser(userRepository.getReferenceById(checkerUserId));
    req.setDecidedAt(Instant.now());
    Map<String, Object> p = new HashMap<>();
    if (req.getPayload() != null) {
      p.putAll(req.getPayload());
    }
    p.put("rejectReason", reason == null ? "" : reason);
    req.setPayload(p);
    approvalRequestRepository.save(req);
    auditService.record(
        checkerUserId,
        "WITHDRAWAL_REJECTED",
        "APPROVAL_REQUEST",
        approvalRequestId.toString(),
        Map.of("reason", reason == null ? "" : reason));
  }

  private UUID createWithdrawalApproval(
      UUID makerUserId,
      UUID userId,
      long amountMinor,
      String currency,
      String correlationId,
      String clientIdempotencyKey) {
    ApprovalRequestEntity e = new ApprovalRequestEntity();
    e.setOperationType("WITHDRAWAL");
    e.setPayload(
        Map.of(
            "userId",
            userId.toString(),
            "amountMinor",
            amountMinor,
            "currency",
            currency.toUpperCase(),
            "correlationId",
            correlationId != null ? correlationId : "",
            "clientIdempotencyKey",
            clientIdempotencyKey));
    e.setMakerUser(userRepository.getReferenceById(makerUserId));
    e.setStatus(ApprovalStatus.PENDING);
    return approvalRequestRepository.save(e).getId();
  }

  private FinancialTransactionEntity postWithdrawalLedger(
      UUID actorUserId,
      UUID userId,
      long amountMinor,
      String currency,
      String correlationId,
      String clientIdempotencyKey) {
    return postWithdrawalLedger(
        actorUserId,
        userId,
        amountMinor,
        currency,
        correlationId,
        "withdrawal",
        clientIdempotencyKey);
  }

  private FinancialTransactionEntity postWithdrawalLedger(
      UUID actorUserId,
      UUID userId,
      long amountMinor,
      String currency,
      String correlationId,
      String idempotencyScope,
      String idempotencyKey) {
    String ccy = currency.toUpperCase();
    WalletEntity wallet = walletService.requireUserWallet(userId, ccy);
    walletService.assertWalletSpendable(wallet);
    FinancialTransactionEntity txn =
        ledgerPostingService.postBalancedTransaction(
            FinancialTxnType.WITHDRAWAL,
            ccy,
            idempotencyScope,
            idempotencyKey,
            correlationId,
            null,
            "Withdrawal to provider rails",
            Map.of("userId", userId),
            List.of(
                new PostingLine(
                    wallet.getAccount().getId(),
                    JournalDirection.DEBIT,
                    amountMinor,
                    "Wallet debit"),
                new PostingLine(
                    systemAccountService.settlementAccountId(ccy),
                    JournalDirection.CREDIT,
                    amountMinor,
                    "Provider settlement")));
    afterPosted(actorUserId, txn);
    outboxService.enqueue(
        ledgerProperties.getKafka().getWithdrawalProcessed(),
        txn.getPublicId(),
        Map.of(
            "publicId",
            txn.getPublicId(),
            "amountMinor",
            amountMinor,
            "currency",
            ccy,
            "userId",
            userId));
    return txn;
  }

  @Transactional
  public FinancialTransactionEntity reverse(
      UUID actorUserId, String originalPublicId, String clientIdempotencyKey) {
    FinancialTransactionEntity original =
        financialTransactionRepository
            .findByPublicId(originalPublicId)
            .orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Original txn missing"));
    String idempotencyScope = "reversal";
    List<JournalEntryEntity> lines =
        journalEntryRepository.findByTransaction_Id(original.getId());
    List<PostingLine> reversed =
        lines.stream()
            .map(
                je ->
                    new PostingLine(
                        je.getAccount().getId(),
                        je.getDirection() == JournalDirection.DEBIT
                            ? JournalDirection.CREDIT
                            : JournalDirection.DEBIT,
                        je.getAmountMinor(),
                        "Reversal of " + originalPublicId))
            .toList();
    FinancialTransactionEntity txn =
        ledgerPostingService.postBalancedTransaction(
            FinancialTxnType.REVERSAL,
            original.getCurrency(),
            idempotencyScope,
            clientIdempotencyKey,
            original.getCorrelationId(),
            original,
            "Reversal",
            Map.of("originalPublicId", originalPublicId),
            reversed);
    afterPosted(actorUserId, txn);
    return txn;
  }

  private void afterPosted(UUID actorUserId, FinancialTransactionEntity txn) {
    auditService.record(
        actorUserId,
        "TXN_POSTED",
        "TRANSACTION",
        txn.getPublicId(),
        Map.of("type", txn.getType().name(), "currency", txn.getCurrency()));
    outboxService.enqueue(
        ledgerProperties.getKafka().getTransactionCompleted(),
        txn.getPublicId(),
        Map.of(
            "publicId",
            txn.getPublicId(),
            "type",
            txn.getType().name(),
            "currency",
            txn.getCurrency()));
  }
}
