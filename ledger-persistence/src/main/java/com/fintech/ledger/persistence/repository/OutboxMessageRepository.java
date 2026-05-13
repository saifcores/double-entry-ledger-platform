package com.fintech.ledger.persistence.repository;

import com.fintech.ledger.persistence.entity.OutboxMessageEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface OutboxMessageRepository extends JpaRepository<OutboxMessageEntity, UUID> {

  @Query("select o from OutboxMessageEntity o where o.publishedAt is null "
      + "order by o.createdAt")
  List<OutboxMessageEntity> findUnpublished(Pageable pageable);
}
