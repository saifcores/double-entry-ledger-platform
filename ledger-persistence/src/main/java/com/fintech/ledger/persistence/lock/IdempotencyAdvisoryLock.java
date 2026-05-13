package com.fintech.ledger.persistence.lock;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Component;

/**
 * Serializes concurrent work for the same idempotency key within the current DB transaction.
 */
@Component
public class IdempotencyAdvisoryLock {

  @PersistenceContext
  private EntityManager entityManager;

  public void lockWithinCurrentTransaction(String scope, String idempotencyKey) {
    String token = scope + ":" + idempotencyKey;
    entityManager
        .createNativeQuery(
            "SELECT pg_advisory_xact_lock(hashtext(cast(?1 as text)))")
        .setParameter(1, token)
        .getSingleResult();
  }
}
