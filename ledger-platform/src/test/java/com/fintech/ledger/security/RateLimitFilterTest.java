package com.fintech.ledger.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fintech.ledger.config.LedgerProperties;
import jakarta.servlet.FilterChain;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
class RateLimitFilterTest {

  @Mock private StringRedisTemplate redis;
  @Mock private ValueOperations<String, String> valueOperations;
  @Mock private FilterChain filterChain;

  private LedgerProperties properties;
  private RateLimitFilter filter;

  @BeforeEach
  void setUp() {
    properties = new LedgerProperties();
    filter = new RateLimitFilter(redis, properties);
  }

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
  }

  private void stubIncrement(long count) {
    when(redis.opsForValue()).thenReturn(valueOperations);
    when(valueOperations.increment(anyString())).thenReturn(count);
  }

  @Test
  void ignoresSpoofedForwardedForHeader_whenNotTrusted() throws Exception {
    properties.getRateLimit().setTrustForwardedFor(false);
    stubIncrement(1L);
    MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/login");
    request.setRemoteAddr("10.0.0.5");
    request.addHeader("X-Forwarded-For", "1.2.3.4");
    MockHttpServletResponse response = new MockHttpServletResponse();

    filter.doFilter(request, response, filterChain);

    verify(valueOperations).increment(eq("rate:ip:10.0.0.5"));
    verify(filterChain).doFilter(request, response);
  }

  @Test
  void usesForwardedForHeader_whenTrusted() throws Exception {
    properties.getRateLimit().setTrustForwardedFor(true);
    stubIncrement(1L);
    MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/login");
    request.setRemoteAddr("10.0.0.5");
    request.addHeader("X-Forwarded-For", "1.2.3.4, 10.0.0.1");
    MockHttpServletResponse response = new MockHttpServletResponse();

    filter.doFilter(request, response, filterChain);

    verify(valueOperations).increment(eq("rate:ip:1.2.3.4"));
  }

  @Test
  void authenticatedRequests_areKeyedByUserId_ignoringForwardedFor() throws Exception {
    properties.getRateLimit().setTrustForwardedFor(true);
    stubIncrement(1L);
    UUID userId = UUID.randomUUID();
    LedgerUserDetails principal = new LedgerUserDetails(userId, "u@x.com", "hash", java.util.Set.of("USER"));
    SecurityContextHolder.getContext().setAuthentication(
        new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/payments/transfer");
    request.addHeader("X-Forwarded-For", "1.2.3.4");
    MockHttpServletResponse response = new MockHttpServletResponse();

    filter.doFilter(request, response, filterChain);

    verify(valueOperations).increment(eq("rate:user:" + userId));
  }

  @Test
  void blocksRequest_whenOverLimit() throws Exception {
    properties.getRateLimit().setRequestsPerMinute(5);
    stubIncrement(6L);
    MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/login");
    request.setRemoteAddr("10.0.0.5");
    MockHttpServletResponse response = new MockHttpServletResponse();

    filter.doFilter(request, response, filterChain);

    assertThat(response.getStatus()).isEqualTo(429);
    verify(filterChain, never()).doFilter(request, response);
  }

  @Test
  void allowsRequest_whenWithinLimit() throws Exception {
    properties.getRateLimit().setRequestsPerMinute(5);
    stubIncrement(5L);
    MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/login");
    request.setRemoteAddr("10.0.0.5");
    MockHttpServletResponse response = new MockHttpServletResponse();

    filter.doFilter(request, response, filterChain);

    assertThat(response.getStatus()).isEqualTo(200);
    verify(filterChain).doFilter(request, response);
  }

  @Test
  void skipsHealthAndMetricsEndpoints() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/actuator/health");
    MockHttpServletResponse response = new MockHttpServletResponse();

    filter.doFilter(request, response, filterChain);

    verify(filterChain).doFilter(request, response);
    verify(redis, never()).opsForValue();
  }
}
