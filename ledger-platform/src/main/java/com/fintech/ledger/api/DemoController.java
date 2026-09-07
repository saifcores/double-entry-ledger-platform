package com.fintech.ledger.api;

import com.fintech.ledger.config.LedgerProperties;
import com.fintech.ledger.persistence.entity.UserEntity;
import com.fintech.ledger.persistence.repository.UserRepository;
import com.fintech.ledger.security.SecurityContextSupport;
import com.fintech.ledger.transaction.PaymentApplicationService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Sandbox-only endpoints backing the bundled showcase UI (static/index.html). Guarded by
 * {@code ledger.demo.enabled} (default true here, meant to be turned off for a real
 * production deployment) so the funds faucet never ships live by accident.
 */
@RestController
@RequestMapping("/api/v1/demo")
@Tag(name = "demo")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
public class DemoController {

  private static final List<String> SEEDED_EMAILS =
      List.of(
          "user@ledger.local",
          "merchant@ledger.local",
          "alice@ledger.local",
          "bob@ledger.local",
          "admin@ledger.local");

  private final PaymentApplicationService payments;
  private final SecurityContextSupport security;
  private final LedgerProperties properties;
  private final UserRepository userRepository;

  @PostMapping("/self-deposit")
  public PaymentDtos.TxnResponse selfDeposit(@Valid @RequestBody SelfDepositRequest req) {
    requireDemoEnabled();
    UUID actor = security.requireUserId();
    var txn =
        payments.deposit(
            actor,
            actor,
            "demo-faucet",
            UUID.randomUUID().toString(),
            req.amountMinor(),
            req.currency(),
            "demo-faucet");
    return new PaymentDtos.TxnResponse(
        txn.getPublicId(), txn.getType().name(), txn.getStatus().name(), txn.getCurrency(),
        txn.getCorrelationId());
  }

  @GetMapping("/directory")
  public List<DirectoryEntry> directory() {
    requireDemoEnabled();
    return SEEDED_EMAILS.stream()
        .map(userRepository::findByEmail)
        .filter(java.util.Optional::isPresent)
        .map(java.util.Optional::get)
        .map(this::toEntry)
        .toList();
  }

  private DirectoryEntry toEntry(UserEntity u) {
    return new DirectoryEntry(u.getId(), u.getEmail(), u.getPreferredCurrency());
  }

  private void requireDemoEnabled() {
    if (!properties.getDemo().isEnabled()) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND);
    }
  }

  public record SelfDepositRequest(@Min(1) long amountMinor, @NotBlank String currency) {}

  public record DirectoryEntry(UUID userId, String email, String currency) {}
}
