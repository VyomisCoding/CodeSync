package com.codesync.project.config;

import io.swagger.v3.oas.annotations.enums.SecuritySchemeIn;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
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
    public OpenAPI projectOpenAPI(
            @Value("${server.port}") String serverPort,
            @Value("${codesync.openapi.server-url:}") String configuredServerUrl) {
        String serverUrl = configuredServerUrl == null || configuredServerUrl.isBlank()
                ? "http://localhost:" + serverPort
                : configuredServerUrl;

        return new OpenAPI()
                .info(new io.swagger.v3.oas.models.info.Info()
                        .title("CodeSync Project Service API")
                        .version("1.0.0")
                        .description("Project creation, discovery, forking, starring, and lifecycle APIs"))
                .addServersItem(new Server().url(serverUrl).description("Local development server"));
    }

    @Bean
    public GroupedOpenApi projectDiscoveryApi() {
        return GroupedOpenApi.builder()
                .group("project-discovery")
                .pathsToMatch(
                        "/projects/public",
                        "/projects/search",
                        "/projects/language/**",
                        "/projects/owner/**",
                        "/projects/member/**",
                        "/projects/*"
                )
                .build();
    }

    @Bean
    public GroupedOpenApi projectManagementApi() {
        return GroupedOpenApi.builder()
                .group("project-management")
                .pathsToMatch(
                        "/projects",
                        "/projects/my",
                        "/projects/member/**",
                        "/projects/*/archive",
                        "/projects/*/star",
                        "/projects/*/fork",
                        "/projects/*"
                )
                .build();
    }
}
