package com.fintech.ledger.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fintech.ledger.config.LedgerProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class JwtTokenServiceTest {

  private static final String SECRET = "test-jwt-secret-with-plenty-of-entropy-for-hs256";
  private static final String ISSUER = "ledger-platform-test";

  private LedgerProperties properties;
  private JwtTokenService service;

  @BeforeEach
  void setUp() {
    properties = new LedgerProperties();
    properties.getJwt().setSecret(SECRET);
    properties.getJwt().setIssuer(ISSUER);
    properties.getJwt().setAccessTokenTtlMinutes(60);
    service = new JwtTokenService(properties);
  }

  private LedgerUserDetails user() {
    return new LedgerUserDetails(
        UUID.randomUUID(), "user@ledger.local", "hash", Set.of("USER", "ADMIN"));
  }

  private SecretKey keyFor(String secret) throws Exception {
    byte[] digest = MessageDigest.getInstance("SHA-256").digest(secret.getBytes(StandardCharsets.UTF_8));
    return Keys.hmacShaKeyFor(digest);
  }

  @Test
  void issuedTokenParsesBackToSameSubjectEmailAndRoles() {
    LedgerUserDetails user = user();
    String token = service.issueAccessToken(user);

    Claims claims = service.parse(token);

    assertThat(claims.getSubject()).isEqualTo(user.getId().toString());
    assertThat(claims.get("email", String.class)).isEqualTo(user.getEmail());
    assertThat(claims.getIssuer()).isEqualTo(ISSUER);
    @SuppressWarnings("unchecked")
    List<String> roles = claims.get("roles", List.class);
    assertThat(roles).containsExactlyInAnyOrder("ADMIN", "USER");
  }

  @Test
  void parseRejectsMalformedToken() {
    assertThatThrownBy(() -> service.parse("not-a-jwt"))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("Invalid token");
  }

  @Test
  void parseRejectsTokenSignedWithDifferentSecret() throws Exception {
    String rogueToken = Jwts.builder()
        .issuer(ISSUER)
        .subject(UUID.randomUUID().toString())
        .issuedAt(Date.from(Instant.now()))
        .expiration(Date.from(Instant.now().plusSeconds(3600)))
        .signWith(keyFor("a-completely-different-secret-value"))
        .compact();

    assertThatThrownBy(() -> service.parse(rogueToken))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("Invalid token");
  }

  @Test
  void parseRejectsTokenWithUnexpectedIssuer() throws Exception {
    String token = Jwts.builder()
        .issuer("some-other-issuer")
        .subject(UUID.randomUUID().toString())
        .issuedAt(Date.from(Instant.now()))
        .expiration(Date.from(Instant.now().plusSeconds(3600)))
        .signWith(keyFor(SECRET))
        .compact();

    assertThatThrownBy(() -> service.parse(token))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("Invalid token");
  }

  @Test
  void parseRejectsExpiredToken() throws Exception {
    String token = Jwts.builder()
        .issuer(ISSUER)
        .subject(UUID.randomUUID().toString())
        .issuedAt(Date.from(Instant.now().minusSeconds(7200)))
        .expiration(Date.from(Instant.now().minusSeconds(3600)))
        .signWith(keyFor(SECRET))
        .compact();

    assertThatThrownBy(() -> service.parse(token))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("Invalid token");
  }
}
