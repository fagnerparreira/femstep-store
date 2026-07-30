package com.femstep.store.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI femStepStoreOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("MOVAZU API")
                        .description("E-commerce platform REST API documentation")
                        .version("v1.0.0")
                        .contact(new Contact()
                                .name("MOVAZU Team")
                                .email("contato@movazu.com.br")
                                .url("https://movazu.com.br"))
                        .license(new License()
                                .name("MIT License")
                                .url("https://opensource.org/licenses/MIT")))
                .servers(List.of(
                        new Server()
                                .url("http://localhost:8080/api")
                                .description("Development server"),
                        new Server()
                                .url("https://api.movazu.com.br/api")
                                .description("Production server")
                ))
                .components(new Components()
                        .addSecuritySchemes("bearerAuth",
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                                        .description("JWT authentication token")
                        ))
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"));
    }
}
