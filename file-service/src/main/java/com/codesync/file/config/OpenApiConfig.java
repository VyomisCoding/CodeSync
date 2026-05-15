package com.codesync.file.config;

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
    public OpenAPI fileOpenAPI(
            @Value("${server.port}") String serverPort,
            @Value("${codesync.openapi.server-url:}") String configuredServerUrl) {
        String serverUrl = configuredServerUrl == null || configuredServerUrl.isBlank()
                ? "http://localhost:" + serverPort
                : configuredServerUrl;

        return new OpenAPI()
                .info(new io.swagger.v3.oas.models.info.Info()
                        .title("CodeSync File Service API")
                        .version("1.0.0")
                        .description("Project file tree, folder management, and editor content APIs"))
                .addServersItem(new Server().url(serverUrl).description("Local development server"));
    }

    @Bean
    public GroupedOpenApi fileReadApi() {
        return GroupedOpenApi.builder()
                .group("file-read")
                .pathsToMatch(
                        "/files/*",
                        "/files/*/content",
                        "/files/project/**"
                )
                .build();
    }

    @Bean
    public GroupedOpenApi fileWriteApi() {
        return GroupedOpenApi.builder()
                .group("file-write")
                .pathsToMatch(
                        "/files",
                        "/files/folder",
                        "/files/*/content",
                        "/files/*/rename",
                        "/files/*/move",
                        "/files/*/restore",
                        "/files/*"
                )
                .build();
    }
}
