package com.fintech.ledger.security;

import com.fintech.ledger.persistence.entity.RoleEntity;
import com.fintech.ledger.persistence.entity.UserEntity;
import com.fintech.ledger.persistence.repository.UserRepository;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class LedgerUserDetailsService implements UserDetailsService {

  private final UserRepository userRepository;

  @Override
  @Transactional(readOnly = true)
  public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
    UserEntity user = userRepository
        .findByEmail(username)
        .orElseThrow(() -> new UsernameNotFoundException("User not found"));
    if (user.getStatus() != UserEntity.UserStatus.ACTIVE) {
      throw new ResponseStatusException(HttpStatus.LOCKED, "User inactive");
    }
    if (user.isFrozen()) {
      throw new ResponseStatusException(HttpStatus.LOCKED, "User frozen");
    }
    return new LedgerUserDetails(
        user.getId(),
        user.getEmail(),
        user.getPasswordHash(),
        user.getRoles().stream().map(RoleEntity::getName).collect(Collectors.toSet()));
  }
}
