package com.dravya.setu.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI dravyaSetuOpenAPI(
            @Value("${server.servlet.context-path:}") String contextPath) {

        return new OpenAPI()
                // Resolve against the documentation's origin, including its HTTPS scheme.
                .addServersItem(new Server()
                        .url(contextPath.isBlank() ? "/" : contextPath)
                        .description("This service"))
                .info(new Info()
                        .title("DravyaSetu Backend API")
                        .version("1.0")
                        .description(
                                "REST API for the DravyaSetu medicinal plant identification and knowledge assistance system."))
                .addSecurityItem(
                        new SecurityRequirement()
                                .addList("bearerAuth")
                )
                .components(
                        new Components()
                                .addSecuritySchemes(
                                        "bearerAuth",
                                        new SecurityScheme()
                                                .type(SecurityScheme.Type.HTTP)
                                                .scheme("bearer")
                                                .bearerFormat("JWT")
                                )
                );
    }
}
