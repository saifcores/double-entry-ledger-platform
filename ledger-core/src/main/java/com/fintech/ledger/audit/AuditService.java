package com.fintech.ledger.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fintech.ledger.persistence.entity.AuditLogEntity;
import com.fintech.ledger.persistence.repository.AuditLogRepository;
import com.fintech.ledger.persistence.repository.UserRepository;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuditService {

  private final AuditLogRepository auditLogRepository;
  private final UserRepository userRepository;
  private final ObjectMapper objectMapper;

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void record(
      UUID actorUserId,
      String action,
      String resourceType,
      String resourceId,
      Map<String, Object> payload) {
    AuditLogEntity log = new AuditLogEntity();
    if (actorUserId != null) {
      log.setActorUser(userRepository.getReferenceById(actorUserId));
    }
    log.setAction(action);
    log.setResourceType(resourceType);
    log.setResourceId(resourceId);
    log.setPayload(payload);
    log.setTraceId(MDC.get("traceId"));
    auditLogRepository.save(log);
  }

  public Map<String, Object> jsonSafe(Object value) {
    try {
      String s = objectMapper.writeValueAsString(value);
      return objectMapper.readValue(s, Map.class);
    } catch (JsonProcessingException e) {
      return Map.of("error", "unserializable");
    }
  }
}
