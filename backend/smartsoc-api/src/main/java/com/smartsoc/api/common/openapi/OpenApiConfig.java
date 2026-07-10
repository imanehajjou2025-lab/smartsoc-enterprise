package com.smartsoc.api.common.openapi;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Swagger/OpenAPI documentation with the JWT bearer flow wired in: the
 * "Authorize" button of Swagger UI accepts the access token returned by
 * /api/v1/auth/login and applies it to every try-it-out request.
 */
@Configuration
public class OpenApiConfig {

    private static final String BEARER_SCHEME = "bearerAuth";

    @Bean
    public OpenAPI smartSocOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("SmartSOC Enterprise API")
                        .description("""
                                Unified SOC platform API: alerts, incidents, investigations,
                                threat intelligence, SOAR playbooks and AI assistance.
                                Authenticate via POST /api/v1/auth/login, then use the
                                returned access token as a Bearer token.""")
                        .version("v1")
                        .license(new License().name("MIT")
                                .url("https://github.com/imanehajjou2025-lab/smartsoc-enterprise/blob/main/LICENSE")))
                .components(new Components().addSecuritySchemes(BEARER_SCHEME,
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME));
    }
}
