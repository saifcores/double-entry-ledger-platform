package com.fintech.ledger.notification;

import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationKafkaListener {

  @KafkaListener(topics = "${ledger.kafka.topics.transaction-completed}", groupId = "ledger-notifications")
  public void onTransactionCompleted(@Payload Map<String, Object> payload, Acknowledgment ack) {
    log.info("[notification] transaction completed {}", payload);
    ack.acknowledge();
  }

  @KafkaListener(topics = "${ledger.kafka.topics.payment-failed}", groupId = "ledger-notifications")
  public void onPaymentFailed(@Payload Map<String, Object> payload, Acknowledgment ack) {
    log.warn("[notification] payment failed {}", payload);
    ack.acknowledge();
  }
}
