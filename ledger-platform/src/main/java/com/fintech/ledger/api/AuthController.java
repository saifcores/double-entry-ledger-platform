package com.fintech.ledger.api;

import com.fintech.ledger.auth.AuthDtos;
import com.fintech.ledger.auth.AuthService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "auth")
@RequiredArgsConstructor
public class AuthController {

  private final AuthService authService;

  @PostMapping("/register")
  public AuthDtos.TokenResponse register(@Valid @RequestBody AuthDtos.RegisterRequest req) {
    String token =
        authService.register(req.email(), req.password(), req.preferredCurrency());
    return new AuthDtos.TokenResponse(token);
  }

  @PostMapping("/login")
  public AuthDtos.TokenResponse login(@Valid @RequestBody AuthDtos.LoginRequest req) {
    String token = authService.login(req.email(), req.password());
    return new AuthDtos.TokenResponse(token);
  }
}
