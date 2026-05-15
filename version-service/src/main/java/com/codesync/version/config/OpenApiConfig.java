package com.codesync.version.config;

import io.swagger.v3.oas.annotations.enums.SecuritySchemeIn;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.servers.Server;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@SecurityScheme(
        name = "bearerAuth",
        type = SecuritySchemeType.HTTP,
        scheme = "bearer",
        bearerFormat = "JWT",
        in = SecuritySchemeIn.HEADER
)
public class OpenApiConfig {

    @Bean
    public OpenAPI versionOpenApi(
            @Value("${server.port:8086}") String serverPort,
            @Value("${codesync.openapi.server-url:}") String configuredServerUrl) {
        String serverUrl = configuredServerUrl == null || configuredServerUrl.isBlank()
                ? "http://localhost:" + serverPort
                : configuredServerUrl;

        return new OpenAPI()
                .info(new io.swagger.v3.oas.models.info.Info()
                        .title("CodeSync Version Service API")
                        .version("1.0.0")
                        .description("Snapshot creation, restore, branching, tagging, and diff APIs"))
                .addServersItem(new Server().url(serverUrl).description("Local development server"));
    }

    @Bean
    public GroupedOpenApi versionApi() {
        return GroupedOpenApi.builder()
                .group("version")
                .pathsToMatch("/versions/**")
                .build();
    }
}
