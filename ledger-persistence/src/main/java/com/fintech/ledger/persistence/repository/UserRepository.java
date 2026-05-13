package com.fintech.ledger.persistence.repository;

import com.fintech.ledger.persistence.entity.UserEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<UserEntity, UUID> {

  @EntityGraph(attributePaths = "roles")
  Optional<UserEntity> findByEmail(String email);

  boolean existsByEmail(String email);
}
