package com.fintech.ledger.persistence.repository;

import com.fintech.ledger.persistence.entity.ProviderTransactionEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProviderTransactionRepository
    extends JpaRepository<ProviderTransactionEntity, UUID> {

  Optional<ProviderTransactionEntity> findByProviderAndProviderRef(
      String provider, String providerRef);
}
