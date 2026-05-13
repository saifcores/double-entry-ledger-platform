package com.fintech.ledger.persistence.repository;

import com.fintech.ledger.persistence.entity.JournalEntryEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JournalEntryRepository extends JpaRepository<JournalEntryEntity, UUID> {

    List<JournalEntryEntity> findByTransaction_Id(UUID transactionId);
}
