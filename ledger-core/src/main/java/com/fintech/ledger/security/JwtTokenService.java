package com.fintech.ledger.security;

import com.fintech.ledger.config.LedgerProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Date;
import java.util.Set;
import java.util.stream.Collectors;
import javax.crypto.SecretKey;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class JwtTokenService {

  private final LedgerProperties ledgerProperties;

  public String issueAccessToken(LedgerUserDetails user) {
    Instant now = Instant.now();
    Instant exp = now.plusSeconds(ledgerProperties.getJwt().getAccessTokenTtlMinutes() * 60);
    return Jwts.builder()
        .issuer(ledgerProperties.getJwt().getIssuer())
        .subject(user.getId().toString())
        .claim("email", user.getEmail())
        .claim(
            "roles",
            user.getRoles().stream().sorted().collect(Collectors.toList()))
        .issuedAt(Date.from(now))
        .expiration(Date.from(exp))
        .signWith(signingKey())
        .compact();
  }

  public Claims parse(String token) {
    try {
      return Jwts.parser()
          .verifyWith(signingKey())
          .requireIssuer(ledgerProperties.getJwt().getIssuer())
          .build()
          .parseSignedClaims(token)
          .getPayload();
    } catch (Exception e) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid token");
    }
  }

  private SecretKey signingKey() {
    try {
      byte[] digest = MessageDigest.getInstance("SHA-256")
          .digest(
              ledgerProperties.getJwt().getSecret().getBytes(StandardCharsets.UTF_8));
      return Keys.hmacShaKeyFor(digest);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }
}
