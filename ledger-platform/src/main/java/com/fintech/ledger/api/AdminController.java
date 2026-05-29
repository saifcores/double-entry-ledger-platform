package com.fintech.ledger.api;

import com.fintech.ledger.admin.AdminFreezeService;
import com.fintech.ledger.admin.FraudFlagService;
import com.fintech.ledger.adjustment.AdjustmentApplicationService;
import com.fintech.ledger.security.SecurityContextSupport;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin")
@Tag(name = "admin")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
public class AdminController {

  private final AdminFreezeService adminFreezeService;
  private final FraudFlagService fraudFlagService;
  private final AdjustmentApplicationService adjustmentService;
  private final SecurityContextSupport security;

  public record AdjustmentRequest(
      @NotNull UUID debitAccountId,
      @NotNull UUID creditAccountId,
      @Min(1) long amountMinor,
      @NotBlank String currency,
      @NotBlank String idempotencyKey,
      String reason) {}

  public record FraudFlagRequest(String reason, String severity) {}

  @PostMapping("/wallets/{walletId}/freeze")
  @PreAuthorize("hasAnyRole('ADMIN','OPERATIONS')")
  public Map<String, String> freezeWallet(@PathVariable UUID walletId) {
    adminFreezeService.freezeWallet(security.requireUserId(), walletId);
    return Map.of("status", "FROZEN", "walletId", walletId.toString());
  }

  @PostMapping("/wallets/{walletId}/unfreeze")
  @PreAuthorize("hasAnyRole('ADMIN','OPERATIONS')")
  public Map<String, String> unfreezeWallet(@PathVariable UUID walletId) {
    adminFreezeService.unfreezeWallet(security.requireUserId(), walletId);
    return Map.of("status", "UNFROZEN", "walletId", walletId.toString());
  }

  @PostMapping("/accounts/{accountId}/freeze")
  @PreAuthorize("hasAnyRole('ADMIN','OPERATIONS')")
  public Map<String, String> freezeAccount(@PathVariable UUID accountId) {
    adminFreezeService.freezeAccount(security.requireUserId(), accountId);
    return Map.of("status", "FROZEN", "accountId", accountId.toString());
  }

  @PostMapping("/accounts/{accountId}/unfreeze")
  @PreAuthorize("hasAnyRole('ADMIN','OPERATIONS')")
  public Map<String, String> unfreezeAccount(@PathVariable UUID accountId) {
    adminFreezeService.unfreezeAccount(security.requireUserId(), accountId);
    return Map.of("status", "UNFROZEN", "accountId", accountId.toString());
  }

  @PostMapping("/wallets/{walletId}/fraud-flag")
  @PreAuthorize("hasAnyRole('ADMIN','OPERATIONS','COMPLIANCE')")
  public FraudFlagService.FraudFlagView flagWallet(
      @PathVariable UUID walletId,
      @RequestBody(required = false) FraudFlagRequest body) {
    return fraudFlagService.flagWallet(
        security.requireUserId(),
        walletId,
        body != null ? body.reason() : null,
        body != null ? body.severity() : null);
  }

  @PostMapping("/wallets/{walletId}/fraud-clear")
  @PreAuthorize("hasAnyRole('ADMIN','OPERATIONS','COMPLIANCE')")
  public Map<String, String> clearFraudFlags(@PathVariable UUID walletId) {
    fraudFlagService.clearWalletFlags(security.requireUserId(), walletId);
    return Map.of("status", "CLEARED", "walletId", walletId.toString());
  }

  @GetMapping("/wallets/{walletId}/fraud-flags")
  @PreAuthorize("hasAnyRole('ADMIN','OPERATIONS','COMPLIANCE')")
  public List<FraudFlagService.FraudFlagView> listFraudFlags(@PathVariable UUID walletId) {
    return fraudFlagService.listWalletFlags(walletId);
  }

  @PostMapping("/adjustments")
  @PreAuthorize("hasAnyRole('ADMIN','OPERATIONS')")
  public PaymentDtos.TxnResponse adjustment(@Valid @RequestBody AdjustmentRequest req) {
    var txn =
        adjustmentService.postAdjustment(
            security.requireUserId(),
            req.debitAccountId(),
            req.creditAccountId(),
            req.amountMinor(),
            req.currency(),
            req.reason(),
            req.idempotencyKey());
    return new PaymentDtos.TxnResponse(
        txn.getPublicId(),
        txn.getType().name(),
        txn.getStatus().name(),
        txn.getCurrency(),
        txn.getCorrelationId());
  }
}
