package com.fintech.ledger.auth;

import com.fintech.ledger.persistence.entity.RoleEntity;
import com.fintech.ledger.persistence.entity.UserEntity;
import com.fintech.ledger.persistence.repository.RoleRepository;
import com.fintech.ledger.persistence.repository.UserRepository;
import com.fintech.ledger.security.JwtTokenService;
import com.fintech.ledger.security.LedgerUserDetails;
import com.fintech.ledger.security.LedgerUserDetailsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class AuthService {

  private final UserRepository userRepository;
  private final RoleRepository roleRepository;
  private final PasswordEncoder passwordEncoder;
  private final AuthenticationManager authenticationManager;
  private final LedgerUserDetailsService userDetailsService;
  private final JwtTokenService jwtTokenService;

  @Transactional
  public String register(String email, String password, String preferredCurrency) {
    if (userRepository.existsByEmail(email.toLowerCase())) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Email in use");
    }
    RoleEntity userRole = roleRepository
        .findByName("USER")
        .orElseThrow(
            () -> new ResponseStatusException(HttpStatus.FAILED_DEPENDENCY, "ROLE missing"));
    UserEntity u = new UserEntity();
    u.setEmail(email.toLowerCase());
    u.setPasswordHash(passwordEncoder.encode(password));
    u.setPreferredCurrency(preferredCurrency.toUpperCase());
    u.getRoles().add(userRole);
    userRepository.save(u);
    LedgerUserDetails details = (LedgerUserDetails) userDetailsService.loadUserByUsername(u.getEmail());
    return jwtTokenService.issueAccessToken(details);
  }

  public String login(String email, String password) {
    Authentication auth = authenticationManager.authenticate(
        new UsernamePasswordAuthenticationToken(email.toLowerCase(), password));
    LedgerUserDetails user = (LedgerUserDetails) auth.getPrincipal();
    return jwtTokenService.issueAccessToken(user);
  }
}
