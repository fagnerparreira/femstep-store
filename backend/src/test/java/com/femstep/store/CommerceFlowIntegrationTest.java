package com.femstep.store;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class CommerceFlowIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:15")
            .withDatabaseName("femstep_test")
            .withUsername("postgres")
            .withPassword("integration-test-password");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add(
                "security.jwt.secret",
                () -> "integration-test-jwt-secret-with-more-than-thirty-two-bytes"
        );
        registry.add("security.cookies.secure", () -> false);
        registry.add("payments.sandbox-enabled", () -> true);
        registry.add("security.cors.allowed-origins", () -> "http://localhost:4200");
        registry.add("spring.jpa.show-sql", () -> false);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void customerCanRegisterAddToCartAndCompleteSandboxOrder() throws Exception {
        JsonNode product = json(mockMvc.perform(get("/api/public/products/scarpin-aurora"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        long variantId = product.path("variants").get(0).path("id").asLong();

        String email = "integration-" + UUID.randomUUID() + "@example.test";
        String registerBody = objectMapper.writeValueAsString(Map.of(
                "firstName", "Teste",
                "lastName", "Integração",
                "email", email,
                "password", "TesteSeguro123!"
        ));
        JsonNode auth = json(mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());
        String bearer = "Bearer " + auth.path("token").asText();

        JsonNode cart = json(mockMvc.perform(post("/api/cart/items")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "variantId", variantId,
                                "quantity", 1
                        ))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());
        assertThat(cart.path("totalItems").asInt()).isEqualTo(1);

        Map<String, Object> address = Map.of(
                "recipientName", "Teste Integração",
                "postalCode", "01310100",
                "street", "Avenida Paulista",
                "number", "1000",
                "neighborhood", "Bela Vista",
                "city", "São Paulo",
                "state", "SP"
        );
        JsonNode order = json(mockMvc.perform(post("/api/orders")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "address", address,
                                "couponCode", "BEMVINDA10",
                                "paymentMethod", "SANDBOX_CARD",
                                "idempotencyKey", "test-" + UUID.randomUUID()
                        ))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());

        assertThat(order.path("status").asText()).isEqualTo("PAID");
        assertThat(order.path("paymentStatus").asText()).isEqualTo("APPROVED");
        assertThat(order.path("items").size()).isEqualTo(1);
    }

    private JsonNode json(String value) throws Exception {
        return objectMapper.readTree(value);
    }
}
