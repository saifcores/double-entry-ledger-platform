package com.fintech.ledger.persistence.repository;

import com.fintech.ledger.persistence.entity.FraudFlagEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FraudFlagRepository extends JpaRepository<FraudFlagEntity, UUID> {
}
