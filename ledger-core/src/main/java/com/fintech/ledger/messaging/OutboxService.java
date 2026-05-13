package com.fintech.ledger.messaging;

import com.fintech.ledger.persistence.entity.OutboxMessageEntity;
import com.fintech.ledger.persistence.repository.OutboxMessageRepository;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OutboxService {

  private final OutboxMessageRepository outboxMessageRepository;

  @Transactional
  public void enqueue(String topic, String messageKey, Map<String, Object> payload) {
    OutboxMessageEntity msg = new OutboxMessageEntity();
    msg.setTopic(topic);
    msg.setMessageKey(messageKey);
    msg.setPayload(payload);
    msg.setAttempts(0);
    outboxMessageRepository.save(msg);
  }

  @Transactional
  public void markPublished(UUID id) {
    outboxMessageRepository
        .findById(id)
        .ifPresent(
            m -> {
              m.setPublishedAt(java.time.Instant.now());
              outboxMessageRepository.save(m);
            });
  }
}
