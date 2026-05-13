package com.fintech.ledger.api;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Convenience redirect to Swagger UI (OpenAPI is at {@code /v3/api-docs}).
 */
@Controller
public class SwaggerRedirectController {

  @GetMapping("/api-docs")
  public String apiDocs() {
    return "redirect:/swagger-ui/index.html";
  }
}
