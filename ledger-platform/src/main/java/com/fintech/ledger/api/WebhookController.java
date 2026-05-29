package com.fintech.ledger.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fintech.ledger.transaction.PaymentApplicationService;
import com.fintech.ledger.webhook.WebhookSignatureVerifier;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/webhooks")
@Tag(name = "webhooks")
@RequiredArgsConstructor
public class WebhookController {

  private final WebhookSignatureVerifier signatureVerifier;
  private final PaymentApplicationService payments;
  private final ObjectMapper objectMapper;
  private final Validator validator;

  @PostMapping("/deposits")
  public PaymentDtos.TxnResponse deposit(
      @RequestBody String rawBody,
      @RequestHeader(value = "X-Signature", required = false) String signature) {
    if (!signatureVerifier.isValid(rawBody, signature)) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid webhook signature");
    }
    PaymentDtos.DepositWebhookRequest req;
    try {
      req = objectMapper.readValue(rawBody, PaymentDtos.DepositWebhookRequest.class);
    } catch (Exception e) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid JSON payload");
    }
    Set<ConstraintViolation<PaymentDtos.DepositWebhookRequest>> violations =
        validator.validate(req);
    if (!violations.isEmpty()) {
      String msg =
          violations.stream()
              .map(v -> v.getPropertyPath() + ": " + v.getMessage())
              .collect(Collectors.joining("; "));
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, msg);
    }
    UUID systemActor = req.userId();
    String correlationId =
        req.correlationId() != null && !req.correlationId().isBlank()
            ? req.correlationId()
            : req.providerRef();
    var txn =
        payments.deposit(
            systemActor,
            req.userId(),
            req.provider(),
            req.providerRef(),
            req.amountMinor(),
            req.currency(),
            correlationId);
    return new PaymentDtos.TxnResponse(
        txn.getPublicId(),
        txn.getType().name(),
        txn.getStatus().name(),
        txn.getCurrency(),
        txn.getCorrelationId());
  }
}
