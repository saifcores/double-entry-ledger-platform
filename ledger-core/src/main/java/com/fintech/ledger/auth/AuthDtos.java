package com.fintech.ledger.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AuthDtos() {

  public record RegisterRequest(
      @Email @NotBlank String email,
      @NotBlank @Size(min = 8, max = 128) String password,
      @NotBlank @Size(min = 3, max = 3) String preferredCurrency) {
  }

  public record LoginRequest(
      @Email @NotBlank String email, @NotBlank String password) {
  }

  public record TokenResponse(String accessToken) {
  }
}
