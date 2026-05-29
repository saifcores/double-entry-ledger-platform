package com.fintech.ledger.security;

import com.fintech.ledger.config.LedgerProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
@RequiredArgsConstructor
public class RateLimitFilter extends OncePerRequestFilter {

  private final StringRedisTemplate redis;
  private final LedgerProperties properties;

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    String path = request.getRequestURI();
    return path.startsWith("/actuator/health")
        || path.startsWith("/actuator/prometheus")
        || path.startsWith("/actuator/info")
        || path.startsWith("/v3/api-docs")
        || path.startsWith("/swagger-ui")
        || path.equals("/api-docs");
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    String key = resolveKey(request);
    int limit = properties.getRateLimit().getRequestsPerMinute();
    String redisKey = "rate:" + key;
    Long count = redis.opsForValue().increment(redisKey);
    if (count != null && count == 1L) {
      redis.expire(redisKey, Duration.ofMinutes(1));
    }
    if (count != null && count > limit) {
      response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
      response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
      ProblemDetail pd =
          ProblemDetail.forStatusAndDetail(
              HttpStatus.TOO_MANY_REQUESTS, "Rate limit exceeded");
      pd.setTitle("Too Many Requests");
      response.getWriter().write("{\"title\":\"Too Many Requests\",\"status\":429,\"detail\":\"Rate limit exceeded\"}");
      return;
    }
    filterChain.doFilter(request, response);
  }

  private String resolveKey(HttpServletRequest request) {
    var auth = org.springframework.security.core.context.SecurityContextHolder.getContext()
        .getAuthentication();
    if (auth != null && auth.getPrincipal() instanceof LedgerUserDetails user) {
      return "user:" + user.getId();
    }
    String forwarded = request.getHeader("X-Forwarded-For");
    if (forwarded != null && !forwarded.isBlank()) {
      return "ip:" + forwarded.split(",")[0].trim();
    }
    return "ip:" + request.getRemoteAddr();
  }
}
