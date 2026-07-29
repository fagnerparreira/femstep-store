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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;

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
        Integer notifications = jdbcTemplate.queryForObject("""
                SELECT count(*)
                FROM notification_outbox n
                JOIN orders o ON o.id::text = n.aggregate_id
                WHERE o.public_number = ?
                """, Integer.class, order.path("orderNumber").asText());
        assertThat(notifications).isEqualTo(2);
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
    void apiPropagatesAValidRequestId() throws Exception {
        mockMvc.perform(get("/api/health").header("X-Request-Id", "integration-request-1"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Request-Id", "integration-request-1"));
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
    void customerCanAddListAndRemoveAFavorite() throws Exception {
        long productId = productId("scarpin-aurora");
        String bearer = registerBearer(uniqueEmail("favorite"));

        mockMvc.perform(post("/api/account/favorites/{productId}", productId)
                        .header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isNoContent());
        mockMvc.perform(post("/api/account/favorites/{productId}", productId)
                        .header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isNoContent());

        JsonNode favorites = json(mockMvc.perform(get("/api/account/favorites")
                        .header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        assertThat(favorites.size()).isEqualTo(1);
        assertThat(favorites.get(0).path("id").asLong()).isEqualTo(productId);

        mockMvc.perform(delete("/api/account/favorites/{productId}", productId)
                        .header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isNoContent());

        JsonNode state = json(mockMvc.perform(get("/api/account/favorites/{productId}", productId)
                        .header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        assertThat(state.path("favorite").asBoolean()).isFalse();
    }

    @Test
    void customerCanUpdateTheirProfile() throws Exception {
        String bearer = registerBearer(uniqueEmail("profile"));

        JsonNode profile = json(mockMvc.perform(put("/api/account/profile")
                        .header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "firstName", "Maria",
                                "lastName", "Silva",
                                "cpf", "12345678901"
                        ))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());

        assertThat(profile.path("firstName").asText()).isEqualTo("Maria");
        assertThat(profile.path("lastName").asText()).isEqualTo("Silva");
        assertThat(profile.path("cpf").asText()).isEqualTo("12345678901");
    }

    @Test
    void administratorCanCreateAndDeactivateACoupon() throws Exception {
        String bearer = registerAdminBearer(uniqueEmail("coupon-admin"));
        String code = "TESTE" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        String request = objectMapper.writeValueAsString(Map.of(
                "code", code,
                "description", "Cupom de integração",
                "discountType", "PERCENTAGE",
                "discountValue", 10,
                "minimumOrderValue", 100,
                "usageLimit", 20,
                "usageLimitPerUser", 1,
                "startsAt", java.time.LocalDateTime.now().minusMinutes(1).toString(),
                "endsAt", java.time.LocalDateTime.now().plusDays(2).toString(),
                "active", true
        ));

        JsonNode created = json(mockMvc.perform(post("/api/admin/coupons")
                        .header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());
        long couponId = created.path("id").asLong();
        assertThat(created.path("code").asText()).isEqualTo(code.toUpperCase());

        JsonNode disabled = json(mockMvc.perform(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                .patch("/api/admin/coupons/{couponId}/active", couponId)
                                .header(HttpHeaders.AUTHORIZATION, bearer)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"active\":false}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        assertThat(disabled.path("active").asBoolean()).isFalse();
    }

    @Test
    void administratorCanManageAnEmptyCategory() throws Exception {
        String bearer = registerAdminBearer(uniqueEmail("category-admin"));
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        String slug = "teste-" + suffix;
        String request = objectMapper.writeValueAsString(Map.of(
                "name", "Categoria Teste",
                "slug", slug,
                "description", "Categoria criada pela integração",
                "active", true
        ));

        JsonNode created = json(mockMvc.perform(post("/api/admin/categories")
                        .header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());
        long categoryId = created.path("id").asLong();
        assertThat(created.path("products").asLong()).isZero();

        JsonNode updated = json(mockMvc.perform(put("/api/admin/categories/{categoryId}", categoryId)
                        .header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "Categoria Atualizada",
                                "slug", slug,
                                "description", "Atualizada",
                                "active", true
                        ))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        assertThat(updated.path("name").asText()).isEqualTo("Categoria Atualizada");

        JsonNode disabled = json(mockMvc.perform(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                .patch("/api/admin/categories/{categoryId}/active", categoryId)
                                .header(HttpHeaders.AUTHORIZATION, bearer)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"active\":false}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        assertThat(disabled.path("active").asBoolean()).isFalse();
    }

    @Test
    void administratorCanReadAndUpdateProductDetails() throws Exception {
        String bearer = registerAdminBearer(uniqueEmail("product-admin"));
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        Long categoryId = jdbcTemplate.queryForObject(
                "SELECT id FROM categories WHERE active = true ORDER BY id LIMIT 1",
                Long.class
        );
        String slug = "produto-" + suffix;
        String createRequest = objectMapper.writeValueAsString(Map.ofEntries(
                Map.entry("name", "Produto Teste"),
                Map.entry("slug", slug),
                Map.entry("description", "Descrição inicial"),
                Map.entry("shortDescription", "Resumo inicial"),
                Map.entry("price", 199.90),
                Map.entry("categoryId", categoryId),
                Map.entry("featured", false),
                Map.entry("material", "Sintético"),
                Map.entry("brand", "FemStep"),
                Map.entry("imageUrl", "https://example.test/produto.jpg"),
                Map.entry("variants", List.of(Map.of(
                        "sku", "TEST-" + suffix.toUpperCase(),
                        "size", 36,
                        "color", "Preto",
                        "stock", 3
                )))
        ));

        JsonNode created = json(mockMvc.perform(post("/api/admin/products")
                        .header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createRequest))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());
        long productId = created.path("id").asLong();

        JsonNode details = json(mockMvc.perform(get("/api/admin/products/{productId}", productId)
                        .header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        assertThat(details.path("description").asText()).isEqualTo("Descrição inicial");

        String updatedSlug = slug + "-editado";
        JsonNode updated = json(mockMvc.perform(put("/api/admin/products/{productId}", productId)
                        .header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "Produto Atualizado",
                                "slug", updatedSlug,
                                "description", "Descrição atualizada",
                                "shortDescription", "Resumo atualizado",
                                "price", 219.90,
                                "categoryId", categoryId,
                                "featured", true,
                                "material", "Couro",
                                "brand", "FemStep"
                        ))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());

        assertThat(updated.path("name").asText()).isEqualTo("Produto Atualizado");
        assertThat(updated.path("slug").asText()).isEqualTo(updatedSlug);
        assertThat(updated.path("featured").asBoolean()).isTrue();

        JsonNode variant = json(mockMvc.perform(post("/api/admin/products/{productId}/variants", productId)
                        .header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "sku", "NEW-" + suffix,
                                "size", 37,
                                "color", "Caramelo",
                                "stock", 4,
                                "priceOverride", 229.90
                        ))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());
        long variantId = variant.path("id").asLong();
        assertThat(variant.path("available").asInt()).isEqualTo(4);

        JsonNode editedVariant = json(mockMvc.perform(
                        put("/api/admin/variants/{variantId}", variantId)
                                .header(HttpHeaders.AUTHORIZATION, bearer)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(Map.of(
                                        "sku", "EDIT-" + suffix,
                                        "size", 38,
                                        "color", "Nude",
                                        "priceOverride", 239.90
                                ))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        assertThat(editedVariant.path("size").asInt()).isEqualTo(38);

        JsonNode disabledVariant = json(mockMvc.perform(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                .patch("/api/admin/variants/{variantId}/active", variantId)
                                .header(HttpHeaders.AUTHORIZATION, bearer)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"active\":false}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        assertThat(disabledVariant.path("active").asBoolean()).isFalse();
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

    private long productId(String slug) throws Exception {
        JsonNode product = json(mockMvc.perform(get("/api/public/products/" + slug))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        return product.path("id").asLong();
    }

    private String registerBearer(String email) throws Exception {
        JsonNode auth = json(mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody(email)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());
        return "Bearer " + auth.path("token").asText();
    }

    private String registerAdminBearer(String email) throws Exception {
        registerBearer(email);
        jdbcTemplate.update("""
                INSERT INTO user_roles (user_id, role_id)
                SELECT u.id, r.id FROM users u CROSS JOIN roles r
                WHERE u.email = ? AND r.name = 'ROLE_ADMIN'
                ON CONFLICT DO NOTHING
                """, email);
        JsonNode auth = json(mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", email,
                                "password", "TesteSeguro123!"
                        ))))
                .andExpect(status().isOk())
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
