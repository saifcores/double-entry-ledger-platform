package com.fintech.ledger.api;

import com.fintech.ledger.security.SecurityContextSupport;
import com.fintech.ledger.transaction.PaymentApplicationService;
import com.fintech.ledger.query.LedgerQueryService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotNull;
import java.util.List;
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
@RequestMapping("/api/v1/approvals")
@Tag(name = "approvals")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
public class ApprovalController {

  private final PaymentApplicationService payments;
  private final LedgerQueryService queries;
  private final SecurityContextSupport security;

  public record RejectBody(String reason) {}

  @GetMapping("/pending")
  @PreAuthorize("hasAnyRole('COMPLIANCE','OPERATIONS','ADMIN')")
  public List<LedgerQueryService.ApprovalView> pending() {
    return queries.listPendingApprovals();
  }

  @PostMapping("/{id}/approve")
  @PreAuthorize("hasAnyRole('COMPLIANCE','OPERATIONS','ADMIN')")
  public PaymentDtos.TxnResponse approve(@PathVariable @NotNull UUID id) {
    var txn = payments.approveWithdrawal(security.requireUserId(), id);
    return new PaymentDtos.TxnResponse(
        txn.getPublicId(),
        txn.getType().name(),
        txn.getStatus().name(),
        txn.getCurrency(),
        txn.getCorrelationId());
  }

  @PostMapping("/{id}/reject")
  @PreAuthorize("hasAnyRole('COMPLIANCE','OPERATIONS','ADMIN')")
  public void reject(@PathVariable @NotNull UUID id, @RequestBody(required = false) RejectBody body) {
    payments.rejectWithdrawal(
        security.requireUserId(), id, body != null ? body.reason() : null);
  }
}
