package com.fintech.ledger.messaging;

import com.fintech.ledger.persistence.entity.OutboxMessageEntity;
import com.fintech.ledger.persistence.repository.OutboxMessageRepository;
import java.util.List;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxDispatcher {

  private final OutboxMessageRepository outboxMessageRepository;
  private final KafkaTemplate<String, Object> kafkaTemplate;
  private final OutboxService outboxService;

  @Scheduled(fixedDelayString = "${ledger.outbox.publish-delay-ms:1000}")
  public void publishBatch() {
    List<OutboxMessageEntity> batch = outboxMessageRepository.findUnpublished(PageRequest.of(0, 200));
    for (OutboxMessageEntity m : batch) {
      try {
        kafkaTemplate
            .send(m.getTopic(), m.getMessageKey(), m.getPayload())
            .get(10, TimeUnit.SECONDS);
        outboxService.markPublished(m.getId());
      } catch (Exception ex) {
        log.warn("Outbox publish failed id={}", m.getId(), ex);
        m.setAttempts(m.getAttempts() + 1);
        outboxMessageRepository.save(m);
      }
    }
  }
}
