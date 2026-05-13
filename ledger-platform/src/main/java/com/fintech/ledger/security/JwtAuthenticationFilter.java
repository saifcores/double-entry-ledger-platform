package com.fintech.ledger.security;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Stateless JWT bearer authentication for Spring Security. Public routes skip this filter's
 * logic when no {@code Authorization} header is present.
 */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

  private static final String AUTH_HEADER = "Authorization";
  private static final String BEARER_PREFIX = "Bearer ";

  private final JwtTokenService jwtTokenService;
  private final LedgerUserDetailsService userDetailsService;

  @Override
  protected void doFilterInternal(
      @NonNull HttpServletRequest request,
      @NonNull HttpServletResponse response,
      @NonNull FilterChain filterChain)
      throws ServletException, IOException {
    String header = request.getHeader(AUTH_HEADER);
    if (!StringUtils.hasText(header) || !header.startsWith(BEARER_PREFIX)) {
      filterChain.doFilter(request, response);
      return;
    }
    String raw = header.substring(BEARER_PREFIX.length()).trim();
    if (!StringUtils.hasText(raw)) {
      filterChain.doFilter(request, response);
      return;
    }
    try {
      Claims claims = jwtTokenService.parse(raw);
      String email = claims.get("email", String.class);
      UUID subject = UUID.fromString(claims.getSubject());
      if (email == null) {
        filterChain.doFilter(request, response);
        return;
      }
      UserDetails user = userDetailsService.loadUserByUsername(email);
      if (!(user instanceof LedgerUserDetails ledgerUser)
          || !ledgerUser.getId().equals(subject)) {
        filterChain.doFilter(request, response);
        return;
      }
      if (SecurityContextHolder.getContext().getAuthentication() == null) {
        UsernamePasswordAuthenticationToken auth =
            new UsernamePasswordAuthenticationToken(
                user, null, user.getAuthorities());
        auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(auth);
      }
    } catch (Exception ignored) {
      // Malformed or invalid token: leave context empty; protected routes return 401.
    }
    filterChain.doFilter(request, response);
  }
}
