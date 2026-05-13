package com.fintech.ledger.security;

import java.util.Collection;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

@Getter
public class LedgerUserDetails implements UserDetails {

  private final UUID id;
  private final String email;
  private final String passwordHash;
  private final Set<String> roles;

  public LedgerUserDetails(UUID id, String email, String passwordHash, Set<String> roles) {
    this.id = id;
    this.email = email;
    this.passwordHash = passwordHash;
    this.roles = roles;
  }

  @Override
  public Collection<? extends GrantedAuthority> getAuthorities() {
    return roles.stream().map(r -> new SimpleGrantedAuthority("ROLE_" + r)).collect(Collectors.toSet());
  }

  @Override
  public String getPassword() {
    return passwordHash;
  }

  @Override
  public String getUsername() {
    return email;
  }
}
