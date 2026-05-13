package com.fintech.ledger.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import java.util.Collections;
import java.util.List;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

  @Bean
  OpenAPI openApi(
      @Value("${server.servlet.context-path:}") String contextPath,
      @Value("${springdoc.api-docs.path:/v3/api-docs}") String apiDocsPath) {
    final String scheme = "bearerAuth";
    String base =
        (contextPath == null || contextPath.isBlank()) ? "/" : contextPath;
    return new OpenAPI()
        .servers(List.of(new Server().url(base).description("Ledger API")))
        .info(
            new Info()
                .title("Double-Entry Ledger API")
                .description(
                    "Wallet, double-entry ledger, webhooks, approvals, and ops APIs. "
                        + "Authenticate via **POST /api/v1/auth/login** or **register**, "
                        + "then use **Authorize** with `Bearer <token>`. "
                        + "Raw OpenAPI JSON: **"
                        + apiDocsPath
                        + "**.")
                .version("1.0.0")
                .contact(new Contact().name("Platform team"))
                .license(new License().name("Proprietary").url("about:blank")))
        .addSecurityItem(new SecurityRequirement().addList(scheme))
        .components(
            new Components()
                .addSecuritySchemes(
                    scheme,
                    new SecurityScheme()
                        .name(scheme)
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")
                        .description("JWT from POST /api/v1/auth/login or /register")));
  }

  /**
   * Auth and webhook routes are public; do not show them as requiring a bearer token in Swagger.
   */
  @Bean
  OpenApiCustomizer publicEndpointSecurityCustomizer() {
    return openApi -> {
      if (openApi.getPaths() == null) {
        return;
      }
      openApi
          .getPaths()
          .forEach(
              (path, item) -> {
                if (path.startsWith("/api/v1/auth")
                    || path.startsWith("/api/v1/webhooks")) {
                  item
                      .readOperationsMap()
                      .values()
                      .forEach(op -> op.setSecurity(Collections.emptyList()));
                }
              });
    };
  }
}
