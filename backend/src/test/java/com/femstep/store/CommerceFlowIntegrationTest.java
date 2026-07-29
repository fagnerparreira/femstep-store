package com.femstep.store;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

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

    @Autowired
    private JdbcTemplate jdbcTemplate;

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

    @Test
    void duplicateRegistrationReturnsConflict() throws Exception {
        String email = uniqueEmail("duplicate");
        String body = registerBody(email);

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());

        JsonNode error = json(mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict())
                .andReturn().getResponse().getContentAsString());

        assertThat(error.path("message").asText()).contains("e-mail");
    }

    @Test
    void refreshTokenIsRotatedAndCannotBeReused() throws Exception {
        MvcResult registration = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody(uniqueEmail("refresh"))))
                .andExpect(status().isCreated())
                .andReturn();
        Cookie original = refreshCookie(registration);

        MvcResult refresh = mockMvc.perform(post("/api/auth/refresh").cookie(original))
                .andExpect(status().isOk())
                .andReturn();
        Cookie rotated = refreshCookie(refresh);

        assertThat(rotated.getValue()).isNotEqualTo(original.getValue());
        mockMvc.perform(post("/api/auth/refresh").cookie(original))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/auth/refresh").cookie(rotated))
                .andExpect(status().isOk());
    }

    @Test
    void customerCannotAccessAdministrativeEndpoints() throws Exception {
        String bearer = registerBearer(uniqueEmail("customer"));

        mockMvc.perform(get("/api/admin/dashboard")
                        .header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isForbidden());
    }

    @Test
    void checkoutIsIdempotentForTheSameCustomerKey() throws Exception {
        long variantId = firstVariantId();
        String bearer = registerBearer(uniqueEmail("idempotency"));
        addToCart(bearer, variantId);
        String idempotencyKey = "test-" + UUID.randomUUID();
        String body = checkoutBody("PIX", idempotencyKey);

        JsonNode first = json(mockMvc.perform(post("/api/orders")
                        .header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());
        JsonNode repeated = json(mockMvc.perform(post("/api/orders")
                        .header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());

        assertThat(repeated.path("orderNumber").asText())
                .isEqualTo(first.path("orderNumber").asText());
        assertThat(repeated.path("status").asText()).isEqualTo("PENDING_PAYMENT");
    }

    @Test
    void emptyCartCannotCreateAnOrder() throws Exception {
        String bearer = registerBearer(uniqueEmail("empty-cart"));

        JsonNode error = json(mockMvc.perform(post("/api/orders")
                        .header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(checkoutBody("PIX", "test-" + UUID.randomUUID())))
                .andExpect(status().isBadRequest())
                .andReturn().getResponse().getContentAsString());

        assertThat(error.path("message").asText()).contains("carrinho");
    }

    @Test
    void concurrentCheckoutsCannotSellTheLastUnitTwice() throws Exception {
        long variantId = variantId("tenis-nuvem");
        String firstBearer = registerBearer(uniqueEmail("stock-a"));
        String secondBearer = registerBearer(uniqueEmail("stock-b"));
        addToCart(firstBearer, variantId);
        addToCart(secondBearer, variantId);
        jdbcTemplate.update(
                "UPDATE product_variants SET stock_quantity = 1, reserved_quantity = 0 WHERE id = ?",
                variantId
        );

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Integer> first = executor.submit(() ->
                    concurrentCheckout(firstBearer, ready, start));
            Future<Integer> second = executor.submit(() ->
                    concurrentCheckout(secondBearer, ready, start));

            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            assertThat(List.of(first.get(), second.get()))
                    .containsExactlyInAnyOrder(201, 400);
        } finally {
            executor.shutdownNow();
        }
    }

    private int concurrentCheckout(
            String bearer,
            CountDownLatch ready,
            CountDownLatch start
    ) throws Exception {
        ready.countDown();
        assertThat(start.await(5, TimeUnit.SECONDS)).isTrue();
        return mockMvc.perform(post("/api/orders")
                        .header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(checkoutBody("PIX", "test-" + UUID.randomUUID())))
                .andReturn().getResponse().getStatus();
    }

    private long firstVariantId() throws Exception {
        return variantId("scarpin-aurora");
    }

    private long variantId(String slug) throws Exception {
        JsonNode product = json(mockMvc.perform(get("/api/public/products/" + slug))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        return product.path("variants").get(0).path("id").asLong();
    }

    private String registerBearer(String email) throws Exception {
        JsonNode auth = json(mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody(email)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());
        return "Bearer " + auth.path("token").asText();
    }

    private void addToCart(String bearer, long variantId) throws Exception {
        mockMvc.perform(post("/api/cart/items")
                        .header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "variantId", variantId,
                                "quantity", 1
                        ))))
                .andExpect(status().isCreated());
    }

    private String registerBody(String email) throws Exception {
        return objectMapper.writeValueAsString(Map.of(
                "firstName", "Teste",
                "lastName", "Integração",
                "email", email,
                "password", "TesteSeguro123!"
        ));
    }

    private String checkoutBody(String paymentMethod, String idempotencyKey) throws Exception {
        Map<String, Object> address = Map.of(
                "recipientName", "Teste Integração",
                "postalCode", "01310100",
                "street", "Avenida Paulista",
                "number", "1000",
                "neighborhood", "Bela Vista",
                "city", "São Paulo",
                "state", "SP"
        );
        return objectMapper.writeValueAsString(Map.of(
                "address", address,
                "paymentMethod", paymentMethod,
                "idempotencyKey", idempotencyKey
        ));
    }

    private Cookie refreshCookie(MvcResult result) {
        String header = result.getResponse().getHeader(HttpHeaders.SET_COOKIE);
        assertThat(header).isNotBlank();
        String prefix = "femstep_refresh=";
        String value = header.substring(prefix.length(), header.indexOf(';'));
        return new Cookie("femstep_refresh", value);
    }

    private String uniqueEmail(String prefix) {
        return prefix + "-" + UUID.randomUUID() + "@example.test";
    }

    private JsonNode json(String value) throws Exception {
        return objectMapper.readTree(value);
    }
}
