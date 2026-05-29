package com.fintech.ledger.persistence.repository;

import com.fintech.ledger.persistence.entity.WalletEntity;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WalletRepository extends JpaRepository<WalletEntity, UUID> {

    @EntityGraph(attributePaths = "account")
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select w from WalletEntity w where w.id = :id")
    Optional<WalletEntity> findByIdForUpdate(@Param("id") UUID id);

    @EntityGraph(attributePaths = "account")
    Optional<WalletEntity> findByUser_IdAndCurrency(UUID userId, String currency);

    @EntityGraph(attributePaths = "account")
    Optional<WalletEntity> findByAccountId(UUID accountId);

    @EntityGraph(attributePaths = "account")
    java.util.List<WalletEntity> findByUser_IdOrderByCurrencyAsc(UUID userId);
}
