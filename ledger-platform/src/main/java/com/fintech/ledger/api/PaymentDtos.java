package com.fintech.ledger.api;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public class PaymentDtos {

    public record TransferRequest(
            @NotNull UUID toUserId,
            @Min(1) long amountMinor,
            @NotBlank String currency,
            @NotBlank String idempotencyKey,
            String correlationId) {
    }

    public record MerchantPayRequest(
            @NotNull UUID merchantUserId,
            @Min(1) long grossMinor,
            @Min(0) long feeMinor,
            @NotBlank String currency,
            @NotBlank String idempotencyKey,
            String correlationId) {
    }

    public record WithdrawRequest(
            @Min(1) long amountMinor,
            @NotBlank String currency,
            @NotBlank String idempotencyKey,
            String correlationId) {
    }

    public record ReverseRequest(
            @NotBlank String originalPublicId, @NotBlank String idempotencyKey) {
    }

    public record DepositWebhookRequest(
            @NotNull UUID userId,
            @NotBlank String provider,
            @NotBlank String providerRef,
            @Min(1) long amountMinor,
            @NotBlank String currency,
            String correlationId) {
    }

    public record TxnResponse(
            String publicId, String type, String status, String currency, String correlationId) {
    }

    public record WithdrawPendingResponse(UUID approvalRequestId, String status) {}
}
