package com.fintech.ledger.api;

import com.fintech.ledger.security.SecurityContextSupport;
import com.fintech.ledger.transaction.PaymentApplicationService;
import com.fintech.ledger.transaction.WithdrawOutcome;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/payments")
@Tag(name = "payments")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
public class PaymentController {

  private final PaymentApplicationService payments;
  private final SecurityContextSupport security;

  @PostMapping("/transfer")
  public PaymentDtos.TxnResponse transfer(@Valid @RequestBody PaymentDtos.TransferRequest req) {
    UUID actor = security.requireUserId();
    var txn = payments.transfer(
        actor,
        actor,
        req.toUserId(),
        req.amountMinor(),
        req.currency(),
        req.correlationId() == null ? UUID.randomUUID().toString() : req.correlationId(),
        req.idempotencyKey());
    return toDto(txn);
  }

  @PostMapping("/merchant")
  public PaymentDtos.TxnResponse merchant(@Valid @RequestBody PaymentDtos.MerchantPayRequest req) {
    UUID payer = security.requireUserId();
    var txn = payments.merchantPayment(
        payer,
        payer,
        req.merchantUserId(),
        req.grossMinor(),
        req.feeMinor(),
        req.currency(),
        req.correlationId() == null ? UUID.randomUUID().toString() : req.correlationId(),
        req.idempotencyKey());
    return toDto(txn);
  }

  @PostMapping("/withdraw")
  public ResponseEntity<?> withdraw(@Valid @RequestBody PaymentDtos.WithdrawRequest req) {
    UUID actor = security.requireUserId();
    WithdrawOutcome outcome =
        payments.withdraw(
            actor,
            actor,
            req.amountMinor(),
            req.currency(),
            req.correlationId() == null ? UUID.randomUUID().toString() : req.correlationId(),
            req.idempotencyKey());
    if (outcome.isPendingApproval()) {
      return ResponseEntity.status(HttpStatus.ACCEPTED)
          .body(
              new PaymentDtos.WithdrawPendingResponse(
                  outcome.pendingApprovalId(), "PENDING_APPROVAL"));
    }
    return ResponseEntity.ok(toDto(outcome.transaction()));
  }

  @PostMapping("/reverse")
  public PaymentDtos.TxnResponse reverse(@Valid @RequestBody PaymentDtos.ReverseRequest req) {
    UUID actor = security.requireUserId();
    var txn = payments.reverse(actor, req.originalPublicId(), req.idempotencyKey());
    return toDto(txn);
  }

  private static PaymentDtos.TxnResponse toDto(
      com.fintech.ledger.persistence.entity.FinancialTransactionEntity txn) {
    return new PaymentDtos.TxnResponse(
        txn.getPublicId(),
        txn.getType().name(),
        txn.getStatus().name(),
        txn.getCurrency(),
        txn.getCorrelationId());
  }
}
