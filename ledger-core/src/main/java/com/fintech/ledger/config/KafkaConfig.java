package com.fintech.ledger.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.DeserializationException;
import org.springframework.util.backoff.FixedBackOff;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class KafkaConfig {

  private final LedgerProperties ledgerProperties;

  /**
   * Recover (seek past) failed records instead of spinning on the same offset.
   * Paired with
   * {@code ErrorHandlingDeserializer} so deserialization failures are visible to
   * this handler.
   */
  @Bean
  CommonErrorHandler kafkaErrorHandler() {
    DefaultErrorHandler handler = new DefaultErrorHandler(KafkaConfig::logAndSkip, new FixedBackOff(0L, 0L));
    handler.addNotRetryableExceptions(DeserializationException.class);
    return handler;
  }

  private static void logAndSkip(ConsumerRecord<?, ?> record, Exception exception) {
    log.error(
        "Skipping Kafka record topic={} partition={} offset={} key={}",
        record.topic(),
        record.partition(),
        record.offset(),
        record.key(),
        exception);
  }

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
