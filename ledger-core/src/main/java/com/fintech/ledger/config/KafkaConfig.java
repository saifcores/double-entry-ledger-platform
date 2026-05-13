package com.fintech.ledger.config;

import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
@RequiredArgsConstructor
public class KafkaConfig {

  private final LedgerProperties ledgerProperties;

  @Bean
  NewTopic transactionCompletedTopic() {
    return TopicBuilder
        .name(ledgerProperties.getKafka().getTransactionCompleted())
        .partitions(3)
        .replicas(1)
        .build();
  }

  @Bean
  NewTopic paymentFailedTopic() {
    return TopicBuilder
        .name(ledgerProperties.getKafka().getPaymentFailed())
        .partitions(3)
        .replicas(1)
        .build();
  }

  @Bean
  NewTopic reconMismatchTopic() {
    return TopicBuilder
        .name(ledgerProperties.getKafka().getReconciliationMismatch())
        .partitions(2)
        .replicas(1)
        .build();
  }

  @Bean
  NewTopic withdrawalProcessedTopic() {
    return TopicBuilder
        .name(ledgerProperties.getKafka().getWithdrawalProcessed())
        .partitions(3)
        .replicas(1)
        .build();
  }
}
