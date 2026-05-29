package com.fintech.ledger.persistence.repository;

import com.fintech.ledger.persistence.entity.AccountEntity;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AccountRepository extends JpaRepository<AccountEntity, UUID> {

  Optional<AccountEntity> findByCode(String code);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select a from AccountEntity a where a.id = :id")
  Optional<AccountEntity> findByIdForUpdate(UUID id);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select a from AccountEntity a where a.id in :ids")
  List<AccountEntity> findAllByIdForUpdate(@Param("ids") Collection<UUID> ids);

  List<AccountEntity> findByCurrencyOrderByCodeAsc(String currency);

  List<AccountEntity> findAllByOrderByCodeAsc();
}
