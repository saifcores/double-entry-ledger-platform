package com.fintech.ledger.api;

import com.fintech.ledger.admin.AdminFreezeService;
import com.fintech.ledger.security.SecurityContextSupport;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin")
@Tag(name = "admin")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
public class AdminController {

  private final AdminFreezeService adminFreezeService;
  private final SecurityContextSupport security;

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
}
