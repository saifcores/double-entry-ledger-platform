package com.fintech.ledger.persistence.entity;

import com.fintech.ledger.domain.FinancialTxnStatus;
import com.fintech.ledger.domain.FinancialTxnType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Getter
@Setter
@Entity
@Table(name = "transactions")
public class FinancialTransactionEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "public_id", nullable = false, unique = true, length = 64)
  private String publicId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 32)
  private FinancialTxnType type;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 32)
  private FinancialTxnStatus status;

  @Column(nullable = false, length = 3)
  private String currency;

  @Column(name = "idempotency_key", nullable = false, length = 128)
  private String idempotencyKey;

  @Column(name = "idempotency_scope", nullable = false, length = 64)
  private String idempotencyScope;

  @Column(name = "correlation_id", length = 128)
  private String correlationId;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "related_transaction_id")
  private FinancialTransactionEntity relatedTransaction;

  private String description;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(columnDefinition = "jsonb")
  private Map<String, Object> metadata = new HashMap<>();

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;
}
