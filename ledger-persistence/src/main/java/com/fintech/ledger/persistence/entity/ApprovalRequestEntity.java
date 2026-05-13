package com.fintech.ledger.persistence.entity;

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
@Table(name = "approval_requests")
public class ApprovalRequestEntity {

  public enum ApprovalStatus {
    PENDING,
    APPROVED,
    REJECTED
  }

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "operation_type", nullable = false, length = 64)
  private String operationType;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(nullable = false, columnDefinition = "jsonb")
  private Map<String, Object> payload;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 32)
  private ApprovalStatus status = ApprovalStatus.PENDING;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "maker_user_id", nullable = false)
  private UserEntity makerUser;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "checker_user_id")
  private UserEntity checkerUser;

  @Column(name = "decided_at")
  private Instant decidedAt;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;
}
