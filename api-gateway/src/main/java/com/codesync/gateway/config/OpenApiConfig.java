package com.codesync.gateway.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(
        info = @Info(
                title = "CodeSync API Gateway",
                version = "1.0.0",
                description = "Edge routing and aggregated API documentation for CodeSync services"
        )
)
public class OpenApiConfig {

    @Bean
    public GroupedOpenApi gatewayManagementApi() {
        return GroupedOpenApi.builder()
                .group("gateway-management")
                .pathsToMatch("/actuator/**")
                .build();
    }
}
