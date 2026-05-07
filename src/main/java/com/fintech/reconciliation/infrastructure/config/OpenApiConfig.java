package com.fintech.reconciliation.infrastructure.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI paymentReconciliationOpenAPI() {
        return new OpenAPI()
            .info(new Info()
                .title("Payment Reconciliation API")
                .description("""
                    API for payment reconciliation between the internal system and the external processor.

                    Verifies data parity (amounts, dates, statuses) to guarantee
                    the financial integrity of transactions.
                    """)
                .version("1.0.0")
                .contact(new Contact()
                    .name("FinTech Platform Team")
                    .email("platform@fintech.com")
                )
                .license(new License().name("Proprietary"))
            )
            .servers(List.of(
                new Server().url("/api").description("Default server")
            ))
            .addSecurityItem(new SecurityRequirement().addList("bearerAuth"))
            .components(new Components()
                .addSecuritySchemes("bearerAuth", new SecurityScheme()
                    .type(SecurityScheme.Type.HTTP)
                    .scheme("bearer")
                    .bearerFormat("JWT")
                )
            );
    }
}
