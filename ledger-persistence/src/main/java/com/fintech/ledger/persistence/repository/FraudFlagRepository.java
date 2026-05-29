package com.fintech.ledger.persistence.repository;

import com.fintech.ledger.persistence.entity.FraudFlagEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FraudFlagRepository extends JpaRepository<FraudFlagEntity, UUID> {

  boolean existsBySubjectTypeAndSubjectIdAndActiveTrue(String subjectType, UUID subjectId);

  List<FraudFlagEntity> findBySubjectTypeAndSubjectIdOrderByCreatedAtDesc(
      String subjectType, UUID subjectId);
}
