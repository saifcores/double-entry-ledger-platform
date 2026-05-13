package com.fintech.ledger.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import java.util.Map;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Data
@Validated
@ConfigurationProperties(prefix = "ledger")
public class LedgerProperties {

  @NotBlank
  private String systemUserEmail = "system@ledger.internal";

  private final Jwt jwt = new Jwt();

  private final Webhook webhook = new Webhook();

  private final RateLimit rateLimit = new RateLimit();

  private final KafkaTopics kafka = new KafkaTopics();

  private final Withdrawal withdrawal = new Withdrawal();

  @Data
  public static class Withdrawal {

    /**
     * Amounts at or above this (minor units) require maker/checker approval before posting.
     * Default {@code Long.MAX_VALUE} disables the workflow.
     */
    private long approvalThresholdMinor = Long.MAX_VALUE;
  }

  @Data
  public static class Jwt {

    @NotBlank
    private String secret;

    @NotBlank
    private String issuer = "ledger-platform";

    @Positive
    private long accessTokenTtlMinutes = 60;
  }

  @Data
  public static class Webhook {

    @NotBlank
    private String hmacSecret = "dev-hmac";
  }

  @Data
  public static class RateLimit {

    @Positive
    private int requestsPerMinute = 120;
  }

  @Data
  public static class KafkaTopics {

    private String transactionCompleted = "ledger.transaction.completed";
    private String paymentFailed = "ledger.payment.failed";
    private String reconciliationMismatch = "ledger.reconciliation.mismatch";
    private String withdrawalProcessed = "ledger.withdrawal.processed";

    public Map<String, String> all() {
      return Map.of(
          "transactionCompleted",
          transactionCompleted,
          "paymentFailed",
          paymentFailed,
          "reconciliationMismatch",
          reconciliationMismatch,
          "withdrawalProcessed",
          withdrawalProcessed);
    }
  }
}
