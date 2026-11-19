package com.example.touristpermit.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    private static final String BEARER = "bearerAuth";

    @Bean
    OpenAPI touristPermitOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("YatraPass API - Digital Tourist Permit & QR Verification")
                        .version("v1")
                        .description("""
                                REST API for tourist registration, permit applications, permit issuance with \
                                secure QR codes, and QR verification by administrators/officers.

                                Authenticate via `POST /api/v1/auth/login`, then click **Authorize** and paste \
                                the `accessToken`. Errors use a uniform envelope \
                                (`timestamp, status, error, message, path`)."""))
                .components(new Components().addSecuritySchemes(BEARER, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER));
    }
}
