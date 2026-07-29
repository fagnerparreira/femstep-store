package com.femstep.store.service;

import com.femstep.store.dto.admin.AdminDashboardResponse;
import com.femstep.store.dto.admin.AdminCouponRequest;
import com.femstep.store.dto.admin.AdminCouponResponse;
import com.femstep.store.dto.admin.AdminCategoryRequest;
import com.femstep.store.dto.admin.AdminCategoryResponse;
import com.femstep.store.dto.admin.AdminOrderResponse;
import com.femstep.store.dto.admin.AdminProductDetailsResponse;
import com.femstep.store.dto.admin.AdminProductImageRequest;
import com.femstep.store.dto.admin.AdminProductImageResponse;
import com.femstep.store.dto.admin.AdminProductRequest;
import com.femstep.store.dto.admin.AdminProductResponse;
import com.femstep.store.dto.admin.AdminProductUpdateRequest;
import com.femstep.store.dto.admin.AdminVariantCreateRequest;
import com.femstep.store.dto.admin.AdminVariantResponse;
import com.femstep.store.dto.admin.AdminVariantUpdateRequest;
import com.femstep.store.exception.BusinessRuleException;
import com.femstep.store.exception.ConflictException;
import com.femstep.store.exception.ResourceNotFoundException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Service
public class AdminService {

    private static final Map<String, String> NEXT_STATUS = Map.of(
            "PAID", "PROCESSING",
            "PROCESSING", "SHIPPED",
            "SHIPPED", "DELIVERED"
    );

    private final JdbcTemplate jdbcTemplate;
    private final NotificationOutboxService notificationOutboxService;

    public AdminService(
            JdbcTemplate jdbcTemplate,
            NotificationOutboxService notificationOutboxService
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.notificationOutboxService = notificationOutboxService;
    }

    @Transactional(readOnly = true)
    public AdminDashboardResponse dashboard() {
        return new AdminDashboardResponse(
                count("SELECT count(*) FROM products"),
                count("SELECT count(*) FROM products WHERE active = true"),
                count("""
                        SELECT count(*) FROM product_variants
                        WHERE active = true AND stock_quantity - reserved_quantity <= 3
                        """),
                count("SELECT count(*) FROM orders"),
                count("SELECT count(*) FROM orders WHERE status IN ('PENDING_PAYMENT', 'PAID', 'PROCESSING')"),
                amount("SELECT COALESCE(sum(grand_total), 0) FROM orders WHERE payment_status = 'APPROVED'")
        );
    }

    @Transactional(readOnly = true)
    public List<AdminProductResponse> products() {
        List<AdminProductResponse> products = jdbcTemplate.query("""
                        SELECT p.id, p.name, p.slug, c.name AS category,
                               p.base_price, p.active, p.featured,
                               count(pv.id) AS variants,
                               COALESCE(sum(pv.stock_quantity), 0) AS total_stock,
                               COALESCE(sum(pv.reserved_quantity), 0) AS reserved_stock
                        FROM products p
                        JOIN categories c ON c.id = p.category_id
                        LEFT JOIN product_variants pv ON pv.product_id = p.id
                        GROUP BY p.id, c.name
                        ORDER BY p.created_at DESC, p.id DESC
                        """,
                (rs, rowNum) -> new AdminProductResponse(
                        rs.getLong("id"),
                        rs.getString("name"),
                        rs.getString("slug"),
                        rs.getString("category"),
                        rs.getBigDecimal("base_price"),
                        rs.getBoolean("active"),
                        rs.getBoolean("featured"),
                        rs.getLong("variants"),
                        rs.getLong("total_stock"),
                        rs.getLong("reserved_stock"),
                        List.of()
                ));
        return products.stream()
                .map(product -> withInventory(product, inventory(product.id())))
                .toList();
    }

    @Transactional(readOnly = true)
    public AdminProductDetailsResponse productDetails(Long productId) {
        return jdbcTemplate.query("""
                        SELECT id, name, slug, description, short_description,
                               base_price, category_id, active, featured, material, brand
                        FROM products
                        WHERE id = ?
                        """,
                (rs, rowNum) -> new AdminProductDetailsResponse(
                        rs.getLong("id"),
                        rs.getString("name"),
                        rs.getString("slug"),
                        rs.getString("description"),
                        rs.getString("short_description"),
                        rs.getBigDecimal("base_price"),
                        rs.getLong("category_id"),
                        rs.getBoolean("active"),
                        rs.getBoolean("featured"),
                        rs.getString("material"),
                        rs.getString("brand")
                ),
                productId
        ).stream().findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Produto não encontrado"));
    }

    @Transactional
    public AdminProductResponse createProduct(AdminProductRequest request, String adminEmail) {
        ensureCategory(request.categoryId());
        try {
            Long productId = jdbcTemplate.queryForObject("""
                    INSERT INTO products (
                        name, slug, description, short_description, base_price,
                        active, featured, category_id, material, brand, published_at
                    ) VALUES (?, ?, ?, ?, ?, true, ?, ?, ?, ?, CURRENT_TIMESTAMP)
                    RETURNING id
                    """,
                    Long.class,
                    request.name().trim(),
                    request.slug().trim(),
                    request.description().trim(),
                    blankToNull(request.shortDescription()),
                    request.price(),
                    request.featured(),
                    request.categoryId(),
                    blankToNull(request.material()),
                    blankToNull(request.brand()) == null ? "FemStep" : request.brand().trim()
            );
            jdbcTemplate.update("""
                    INSERT INTO product_images (
                        url, alt_text, display_order, main_image, product_id
                    ) VALUES (?, ?, 0, true, ?)
                    """, request.imageUrl().trim(), request.name().trim(), productId);
            for (AdminProductRequest.VariantRequest variant : request.variants()) {
                jdbcTemplate.update("""
                        INSERT INTO product_variants (
                            sku, size, color, stock_quantity, reserved_quantity,
                            price_override, active, product_id
                        ) VALUES (?, ?, ?, ?, 0, NULL, true, ?)
                        """,
                        variant.sku().trim().toUpperCase(),
                        variant.size(),
                        variant.color().trim(),
                        variant.stock(),
                        productId
                );
            }
            audit(adminEmail, "CREATE_PRODUCT", "PRODUCT", productId, request.slug());
            return product(productId);
        } catch (DuplicateKeyException exception) {
            throw new ConflictException("Já existe um produto, SKU ou variação com estes dados");
        }
    }

    @Transactional
    public AdminProductResponse updateProduct(
            Long productId,
            AdminProductUpdateRequest request,
            String adminEmail
    ) {
        ensureCategory(request.categoryId());
        try {
            int updated = jdbcTemplate.update("""
                    UPDATE products
                    SET name = ?, slug = ?, description = ?, short_description = ?,
                        base_price = ?, category_id = ?, featured = ?, material = ?,
                        brand = ?, updated_at = CURRENT_TIMESTAMP
                    WHERE id = ?
                    """,
                    request.name().trim(),
                    request.slug().trim(),
                    request.description().trim(),
                    blankToNull(request.shortDescription()),
                    request.price(),
                    request.categoryId(),
                    request.featured(),
                    blankToNull(request.material()),
                    blankToNull(request.brand()) == null ? "FemStep" : request.brand().trim(),
                    productId
            );
            if (updated != 1) {
                throw new ResourceNotFoundException("Produto não encontrado");
            }
            audit(adminEmail, "UPDATE_PRODUCT", "PRODUCT", productId, request.slug());
            return product(productId);
        } catch (DuplicateKeyException exception) {
            throw new ConflictException("Já existe um produto com este slug");
        }
    }

    @Transactional
    public AdminProductResponse toggleProduct(Long productId, boolean active, String adminEmail) {
        int updated = jdbcTemplate.update(
                "UPDATE products SET active = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?",
                active,
                productId
        );
        if (updated != 1) {
            throw new ResourceNotFoundException("Produto não encontrado");
        }
        audit(adminEmail, active ? "ACTIVATE_PRODUCT" : "DEACTIVATE_PRODUCT", "PRODUCT", productId, null);
        return product(productId);
    }

    @Transactional(readOnly = true)
    public List<AdminProductImageResponse> productImages(Long productId) {
        ensureProduct(productId);
        return jdbcTemplate.query("""
                        SELECT id, url, alt_text, display_order, main_image
                        FROM product_images
                        WHERE product_id = ?
                        ORDER BY main_image DESC, display_order, id
                        """,
                (rs, rowNum) -> new AdminProductImageResponse(
                        rs.getLong("id"),
                        rs.getString("url"),
                        rs.getString("alt_text"),
                        rs.getInt("display_order"),
                        rs.getBoolean("main_image")
                ),
                productId
        );
    }

    @Transactional
    public AdminProductImageResponse createProductImage(
            Long productId,
            AdminProductImageRequest request,
            String adminEmail
    ) {
        ensureProduct(productId);
        boolean makeMain = request.mainImage() || countProductImages(productId) == 0;
        if (makeMain) {
            clearMainImage(productId);
        }
        Long imageId = jdbcTemplate.queryForObject("""
                INSERT INTO product_images (
                    url, alt_text, display_order, main_image, product_id
                ) VALUES (?, ?, ?, ?, ?)
                RETURNING id
                """,
                Long.class,
                request.url().trim(),
                request.altText().trim(),
                request.displayOrder(),
                makeMain,
                productId
        );
        audit(adminEmail, "CREATE_PRODUCT_IMAGE", "PRODUCT_IMAGE", imageId, request.url());
        return productImage(imageId);
    }

    @Transactional
    public AdminProductImageResponse updateProductImage(
            Long imageId,
            AdminProductImageRequest request,
            String adminEmail
    ) {
        ImageState current = imageState(imageId);
        if (request.mainImage()) {
            clearMainImage(current.productId());
        } else if (current.mainImage()) {
            throw new BusinessRuleException(
                    "Defina outra imagem como principal antes de remover esta seleção"
            );
        }
        jdbcTemplate.update("""
                UPDATE product_images
                SET url = ?, alt_text = ?, display_order = ?, main_image = ?,
                    updated_at = CURRENT_TIMESTAMP
                WHERE id = ?
                """,
                request.url().trim(),
                request.altText().trim(),
                request.displayOrder(),
                request.mainImage(),
                imageId
        );
        audit(adminEmail, "UPDATE_PRODUCT_IMAGE", "PRODUCT_IMAGE", imageId, request.url());
        return productImage(imageId);
    }

    @Transactional
    public void deleteProductImage(Long imageId, String adminEmail) {
        ImageState current = imageState(imageId);
        if (countProductImages(current.productId()) <= 1) {
            throw new BusinessRuleException("O produto precisa manter ao menos uma imagem");
        }
        jdbcTemplate.update("DELETE FROM product_images WHERE id = ?", imageId);
        if (current.mainImage()) {
            jdbcTemplate.update("""
                    UPDATE product_images
                    SET main_image = true, updated_at = CURRENT_TIMESTAMP
                    WHERE id = (
                        SELECT id FROM product_images
                        WHERE product_id = ?
                        ORDER BY display_order, id
                        LIMIT 1
                    )
                    """, current.productId());
        }
        audit(adminEmail, "DELETE_PRODUCT_IMAGE", "PRODUCT_IMAGE", imageId, null);
    }

    @Transactional
    public AdminVariantResponse createVariant(
            Long productId,
            AdminVariantCreateRequest request,
            String adminEmail
    ) {
        ensureProduct(productId);
        try {
            Long variantId = jdbcTemplate.queryForObject("""
                    INSERT INTO product_variants (
                        sku, size, color, stock_quantity, reserved_quantity,
                        price_override, active, product_id
                    ) VALUES (?, ?, ?, ?, 0, ?, true, ?)
                    RETURNING id
                    """,
                    Long.class,
                    request.sku().trim().toUpperCase(),
                    request.size(),
                    request.color().trim(),
                    request.stock(),
                    request.priceOverride(),
                    productId
            );
            if (request.stock() > 0) {
                jdbcTemplate.update("""
                        INSERT INTO inventory_movements (
                            variant_id, movement_type, quantity, stock_before,
                            stock_after, reason, created_by_user_id
                        ) VALUES (?, 'INBOUND', ?, 0, ?, 'Estoque inicial da variação', ?)
                        """,
                        variantId,
                        request.stock(),
                        request.stock(),
                        userId(adminEmail)
                );
            }
            audit(adminEmail, "CREATE_VARIANT", "PRODUCT_VARIANT", variantId, request.sku());
            return variant(variantId);
        } catch (DuplicateKeyException exception) {
            throw new ConflictException("Já existe um SKU ou uma variação com estes dados");
        }
    }

    @Transactional
    public AdminVariantResponse updateVariant(
            Long variantId,
            AdminVariantUpdateRequest request,
            String adminEmail
    ) {
        try {
            int updated = jdbcTemplate.update("""
                    UPDATE product_variants
                    SET sku = ?, size = ?, color = ?, price_override = ?,
                        updated_at = CURRENT_TIMESTAMP
                    WHERE id = ?
                    """,
                    request.sku().trim().toUpperCase(),
                    request.size(),
                    request.color().trim(),
                    request.priceOverride(),
                    variantId
            );
            if (updated != 1) {
                throw new ResourceNotFoundException("Variação não encontrada");
            }
            audit(adminEmail, "UPDATE_VARIANT", "PRODUCT_VARIANT", variantId, request.sku());
            return variant(variantId);
        } catch (DuplicateKeyException exception) {
            throw new ConflictException("Já existe um SKU ou uma variação com estes dados");
        }
    }

    @Transactional
    public AdminVariantResponse toggleVariant(Long variantId, boolean active, String adminEmail) {
        int updated = jdbcTemplate.update(
                "UPDATE product_variants SET active = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?",
                active,
                variantId
        );
        if (updated != 1) {
            throw new ResourceNotFoundException("Variação não encontrada");
        }
        audit(
                adminEmail,
                active ? "ACTIVATE_VARIANT" : "DEACTIVATE_VARIANT",
                "PRODUCT_VARIANT",
                variantId,
                null
        );
        return variant(variantId);
    }

    @Transactional
    public void adjustStock(Long variantId, int stockQuantity, String reason, String adminEmail) {
        VariantStock current = jdbcTemplate.query("""
                        SELECT stock_quantity, reserved_quantity
                        FROM product_variants WHERE id = ? FOR UPDATE
                        """,
                (rs, rowNum) -> new VariantStock(
                        rs.getInt("stock_quantity"),
                        rs.getInt("reserved_quantity")
                ),
                variantId
        ).stream().findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Variação não encontrada"));
        if (stockQuantity < current.reserved()) {
            throw new BusinessRuleException("O estoque não pode ficar abaixo da quantidade reservada");
        }
        jdbcTemplate.update(
                "UPDATE product_variants SET stock_quantity = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?",
                stockQuantity,
                variantId
        );
        Long adminId = userId(adminEmail);
        jdbcTemplate.update("""
                INSERT INTO inventory_movements (
                    variant_id, movement_type, quantity, stock_before,
                    stock_after, reason, created_by_user_id
                ) VALUES (?, 'ADJUSTMENT', ?, ?, ?, ?, ?)
                """,
                variantId,
                stockQuantity - current.stock(),
                current.stock(),
                stockQuantity,
                reason == null || reason.isBlank() ? "Ajuste manual" : reason.trim(),
                adminId
        );
        audit(adminEmail, "ADJUST_STOCK", "PRODUCT_VARIANT", variantId, reason);
    }

    @Transactional(readOnly = true)
    public List<AdminOrderResponse> orders() {
        return jdbcTemplate.query("""
                        SELECT public_number, customer_name, customer_email,
                               status, payment_status, grand_total, created_at
                        FROM orders
                        ORDER BY created_at DESC
                        LIMIT 200
                        """,
                (rs, rowNum) -> new AdminOrderResponse(
                        rs.getString("public_number"),
                        rs.getString("customer_name"),
                        rs.getString("customer_email"),
                        rs.getString("status"),
                        rs.getString("payment_status"),
                        rs.getBigDecimal("grand_total"),
                        rs.getTimestamp("created_at").toLocalDateTime()
                ));
    }

    @Transactional
    public AdminOrderResponse advanceOrder(
            String orderNumber,
            String targetStatus,
            String trackingCode,
            String adminEmail) {
        OrderState order = jdbcTemplate.query("""
                        SELECT id, status, shipping_service, shipping_estimated_days
                        FROM orders WHERE public_number = ? FOR UPDATE
                        """,
                (rs, rowNum) -> new OrderState(
                        rs.getLong("id"),
                        rs.getString("status"),
                        rs.getString("shipping_service"),
                        rs.getInt("shipping_estimated_days")
                ),
                orderNumber
        ).stream().findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Pedido não encontrado"));
        String expected = NEXT_STATUS.get(order.status());
        if (expected == null || !expected.equals(targetStatus)) {
            throw new BusinessRuleException(
                    "Transição inválida. O próximo status permitido é: " + (expected == null ? "nenhum" : expected)
            );
        }
        jdbcTemplate.update(
                "UPDATE orders SET status = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?",
                targetStatus,
                order.id()
        );
        Long adminId = userId(adminEmail);
        jdbcTemplate.update("""
                INSERT INTO order_status_history (
                    order_id, previous_status, new_status, reason, changed_by_user_id
                ) VALUES (?, ?, ?, 'Atualização pelo painel administrativo', ?)
                """, order.id(), order.status(), targetStatus, adminId);
        if (targetStatus.equals("SHIPPED")) {
            if (trackingCode == null || trackingCode.isBlank()) {
                throw new BusinessRuleException("Informe o código de rastreio para marcar como enviado");
            }
            jdbcTemplate.update("""
                    INSERT INTO shipments (
                        order_id, provider, service_name, status, tracking_code,
                        estimated_days, shipped_at
                    ) VALUES (?, 'MANUAL', ?, 'SHIPPED', ?, ?, CURRENT_TIMESTAMP)
                    ON CONFLICT (order_id) DO UPDATE
                    SET status = 'SHIPPED', tracking_code = EXCLUDED.tracking_code,
                        shipped_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP
                    """,
                    order.id(),
                    order.shippingService(),
                    trackingCode.trim(),
                    order.estimatedDays()
            );
            Long customerId = jdbcTemplate.queryForObject(
                    "SELECT user_id FROM orders WHERE id = ?",
                    Long.class,
                    order.id()
            );
            notificationOutboxService.enqueueOrderEvent("ORDER_SHIPPED", order.id(), customerId);
        } else if (targetStatus.equals("DELIVERED")) {
            jdbcTemplate.update("""
                    UPDATE shipments
                    SET status = 'DELIVERED', delivered_at = CURRENT_TIMESTAMP,
                        updated_at = CURRENT_TIMESTAMP
                    WHERE order_id = ?
                    """, order.id());
        }
        audit(adminEmail, "ADVANCE_ORDER", "ORDER", order.id(), targetStatus);
        return order(orderNumber);
    }

    @Transactional(readOnly = true)
    public List<AdminCouponResponse> coupons() {
        return jdbcTemplate.query("""
                        SELECT c.*, count(cr.id) AS uses
                        FROM coupons c
                        LEFT JOIN coupon_redemptions cr ON cr.coupon_id = c.id
                        GROUP BY c.id
                        ORDER BY c.created_at DESC, c.id DESC
                        """,
                (rs, rowNum) -> new AdminCouponResponse(
                        rs.getLong("id"),
                        rs.getString("code"),
                        rs.getString("description"),
                        rs.getString("discount_type"),
                        rs.getBigDecimal("discount_value"),
                        rs.getBigDecimal("minimum_order_value"),
                        rs.getBigDecimal("maximum_discount_value"),
                        (Integer) rs.getObject("usage_limit"),
                        rs.getInt("usage_limit_per_user"),
                        rs.getLong("uses"),
                        rs.getTimestamp("starts_at").toLocalDateTime(),
                        rs.getTimestamp("ends_at").toLocalDateTime(),
                        rs.getBoolean("active")
                ));
    }

    @Transactional
    public AdminCouponResponse createCoupon(AdminCouponRequest request, String adminEmail) {
        validateCoupon(request);
        try {
            Long couponId = jdbcTemplate.queryForObject("""
                    INSERT INTO coupons (
                        code, description, discount_type, discount_value,
                        minimum_order_value, maximum_discount_value,
                        usage_limit, usage_limit_per_user, starts_at, ends_at, active
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    RETURNING id
                    """,
                    Long.class,
                    request.code().trim().toUpperCase(),
                    blankToNull(request.description()),
                    request.discountType(),
                    request.discountValue(),
                    request.minimumOrderValue(),
                    request.maximumDiscountValue(),
                    request.usageLimit(),
                    request.usageLimitPerUser(),
                    request.startsAt(),
                    request.endsAt(),
                    request.active()
            );
            audit(adminEmail, "CREATE_COUPON", "COUPON", couponId, request.code());
            return coupon(couponId);
        } catch (DuplicateKeyException exception) {
            throw new ConflictException("Já existe um cupom com este código");
        }
    }

    @Transactional
    public AdminCouponResponse updateCoupon(
            Long couponId,
            AdminCouponRequest request,
            String adminEmail
    ) {
        validateCoupon(request);
        try {
            int updated = jdbcTemplate.update("""
                    UPDATE coupons
                    SET code = ?, description = ?, discount_type = ?, discount_value = ?,
                        minimum_order_value = ?, maximum_discount_value = ?,
                        usage_limit = ?, usage_limit_per_user = ?, starts_at = ?,
                        ends_at = ?, active = ?, updated_at = CURRENT_TIMESTAMP
                    WHERE id = ?
                    """,
                    request.code().trim().toUpperCase(),
                    blankToNull(request.description()),
                    request.discountType(),
                    request.discountValue(),
                    request.minimumOrderValue(),
                    request.maximumDiscountValue(),
                    request.usageLimit(),
                    request.usageLimitPerUser(),
                    request.startsAt(),
                    request.endsAt(),
                    request.active(),
                    couponId
            );
            if (updated != 1) {
                throw new ResourceNotFoundException("Cupom não encontrado");
            }
            audit(adminEmail, "UPDATE_COUPON", "COUPON", couponId, request.code());
            return coupon(couponId);
        } catch (DuplicateKeyException exception) {
            throw new ConflictException("Já existe um cupom com este código");
        }
    }

    @Transactional
    public AdminCouponResponse toggleCoupon(Long couponId, boolean active, String adminEmail) {
        int updated = jdbcTemplate.update(
                "UPDATE coupons SET active = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?",
                active,
                couponId
        );
        if (updated != 1) {
            throw new ResourceNotFoundException("Cupom não encontrado");
        }
        audit(adminEmail, active ? "ACTIVATE_COUPON" : "DEACTIVATE_COUPON", "COUPON", couponId, null);
        return coupon(couponId);
    }

    @Transactional(readOnly = true)
    public List<AdminCategoryResponse> categories() {
        return jdbcTemplate.query("""
                        SELECT c.id, c.name, c.slug, c.description, c.active,
                               count(p.id) AS products
                        FROM categories c
                        LEFT JOIN products p ON p.category_id = c.id
                        GROUP BY c.id
                        ORDER BY c.name, c.id
                        """,
                (rs, rowNum) -> new AdminCategoryResponse(
                        rs.getLong("id"),
                        rs.getString("name"),
                        rs.getString("slug"),
                        rs.getString("description"),
                        rs.getBoolean("active"),
                        rs.getLong("products")
                ));
    }

    @Transactional
    public AdminCategoryResponse createCategory(
            AdminCategoryRequest request,
            String adminEmail
    ) {
        try {
            Long categoryId = jdbcTemplate.queryForObject("""
                    INSERT INTO categories (name, slug, description, active)
                    VALUES (?, ?, ?, ?)
                    RETURNING id
                    """,
                    Long.class,
                    request.name().trim(),
                    request.slug().trim(),
                    blankToNull(request.description()),
                    request.active()
            );
            audit(adminEmail, "CREATE_CATEGORY", "CATEGORY", categoryId, request.slug());
            return category(categoryId);
        } catch (DuplicateKeyException exception) {
            throw new ConflictException("Já existe uma categoria com este slug");
        }
    }

    @Transactional
    public AdminCategoryResponse updateCategory(
            Long categoryId,
            AdminCategoryRequest request,
            String adminEmail
    ) {
        if (!request.active()) {
            ensureCategoryCanBeDeactivated(categoryId);
        }
        try {
            int updated = jdbcTemplate.update("""
                    UPDATE categories
                    SET name = ?, slug = ?, description = ?, active = ?,
                        updated_at = CURRENT_TIMESTAMP
                    WHERE id = ?
                    """,
                    request.name().trim(),
                    request.slug().trim(),
                    blankToNull(request.description()),
                    request.active(),
                    categoryId
            );
            if (updated != 1) {
                throw new ResourceNotFoundException("Categoria não encontrada");
            }
            audit(adminEmail, "UPDATE_CATEGORY", "CATEGORY", categoryId, request.slug());
            return category(categoryId);
        } catch (DuplicateKeyException exception) {
            throw new ConflictException("Já existe uma categoria com este slug");
        }
    }

    @Transactional
    public AdminCategoryResponse toggleCategory(
            Long categoryId,
            boolean active,
            String adminEmail
    ) {
        if (!active) {
            ensureCategoryCanBeDeactivated(categoryId);
        }
        int updated = jdbcTemplate.update(
                "UPDATE categories SET active = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?",
                active,
                categoryId
        );
        if (updated != 1) {
            throw new ResourceNotFoundException("Categoria não encontrada");
        }
        audit(adminEmail, active ? "ACTIVATE_CATEGORY" : "DEACTIVATE_CATEGORY", "CATEGORY", categoryId, null);
        return category(categoryId);
    }

    private void ensureCategoryCanBeDeactivated(Long categoryId) {
        Long activeProducts = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM products WHERE category_id = ? AND active = true",
                Long.class,
                categoryId
        );
        if (activeProducts != null && activeProducts > 0) {
            throw new BusinessRuleException(
                    "Desative ou mova os produtos ativos antes de desativar a categoria"
            );
        }
    }

    private AdminCategoryResponse category(Long categoryId) {
        return categories().stream()
                .filter(item -> item.id().equals(categoryId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Categoria não encontrada"));
    }

    private AdminCouponResponse coupon(Long couponId) {
        return coupons().stream()
                .filter(item -> item.id().equals(couponId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Cupom não encontrado"));
    }

    private void validateCoupon(AdminCouponRequest request) {
        if (!request.endsAt().isAfter(request.startsAt())) {
            throw new BusinessRuleException("O término do cupom deve ser posterior ao início");
        }
        if ("PERCENTAGE".equals(request.discountType())
                && request.discountValue().compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new BusinessRuleException("O desconto percentual não pode ser maior que 100%");
        }
    }

    private AdminProductResponse product(Long productId) {
        AdminProductResponse product = jdbcTemplate.query("""
                        SELECT p.id, p.name, p.slug, c.name AS category,
                               p.base_price, p.active, p.featured,
                               count(pv.id) AS variants,
                               COALESCE(sum(pv.stock_quantity), 0) AS total_stock,
                               COALESCE(sum(pv.reserved_quantity), 0) AS reserved_stock
                        FROM products p
                        JOIN categories c ON c.id = p.category_id
                        LEFT JOIN product_variants pv ON pv.product_id = p.id
                        WHERE p.id = ?
                        GROUP BY p.id, c.name
                        """,
                (rs, rowNum) -> new AdminProductResponse(
                        rs.getLong("id"),
                        rs.getString("name"),
                        rs.getString("slug"),
                        rs.getString("category"),
                        rs.getBigDecimal("base_price"),
                        rs.getBoolean("active"),
                        rs.getBoolean("featured"),
                        rs.getLong("variants"),
                        rs.getLong("total_stock"),
                        rs.getLong("reserved_stock"),
                        List.of()
                ),
                productId
        ).stream().findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Produto não encontrado"));
        return withInventory(product, inventory(productId));
    }

    private List<AdminVariantResponse> inventory(Long productId) {
        return jdbcTemplate.query("""
                        SELECT id, sku, size, color, price_override, stock_quantity,
                               reserved_quantity, active
                        FROM product_variants
                        WHERE product_id = ?
                        ORDER BY size, color, id
                        """,
                (rs, rowNum) -> new AdminVariantResponse(
                        rs.getLong("id"),
                        rs.getString("sku"),
                        rs.getInt("size"),
                        rs.getString("color"),
                        rs.getBigDecimal("price_override"),
                        rs.getInt("stock_quantity"),
                        rs.getInt("reserved_quantity"),
                        rs.getInt("stock_quantity") - rs.getInt("reserved_quantity"),
                        rs.getBoolean("active")
                ),
                productId
        );
    }

    private AdminVariantResponse variant(Long variantId) {
        return jdbcTemplate.query("""
                        SELECT id, sku, size, color, price_override, stock_quantity,
                               reserved_quantity, active
                        FROM product_variants
                        WHERE id = ?
                        """,
                (rs, rowNum) -> new AdminVariantResponse(
                        rs.getLong("id"),
                        rs.getString("sku"),
                        rs.getInt("size"),
                        rs.getString("color"),
                        rs.getBigDecimal("price_override"),
                        rs.getInt("stock_quantity"),
                        rs.getInt("reserved_quantity"),
                        rs.getInt("stock_quantity") - rs.getInt("reserved_quantity"),
                        rs.getBoolean("active")
                ),
                variantId
        ).stream().findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Variação não encontrada"));
    }

    private AdminProductImageResponse productImage(Long imageId) {
        return jdbcTemplate.query("""
                        SELECT id, url, alt_text, display_order, main_image
                        FROM product_images
                        WHERE id = ?
                        """,
                (rs, rowNum) -> new AdminProductImageResponse(
                        rs.getLong("id"),
                        rs.getString("url"),
                        rs.getString("alt_text"),
                        rs.getInt("display_order"),
                        rs.getBoolean("main_image")
                ),
                imageId
        ).stream().findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Imagem não encontrada"));
    }

    private ImageState imageState(Long imageId) {
        return jdbcTemplate.query("""
                        SELECT product_id, main_image
                        FROM product_images
                        WHERE id = ?
                        FOR UPDATE
                        """,
                (rs, rowNum) -> new ImageState(
                        rs.getLong("product_id"),
                        rs.getBoolean("main_image")
                ),
                imageId
        ).stream().findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Imagem não encontrada"));
    }

    private long countProductImages(Long productId) {
        Long result = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM product_images WHERE product_id = ?",
                Long.class,
                productId
        );
        return result == null ? 0 : result;
    }

    private void clearMainImage(Long productId) {
        jdbcTemplate.update(
                "UPDATE product_images SET main_image = false, updated_at = CURRENT_TIMESTAMP WHERE product_id = ?",
                productId
        );
    }

    private AdminProductResponse withInventory(
            AdminProductResponse product,
            List<AdminVariantResponse> inventory) {
        return new AdminProductResponse(
                product.id(),
                product.name(),
                product.slug(),
                product.category(),
                product.price(),
                product.active(),
                product.featured(),
                product.variants(),
                product.totalStock(),
                product.reservedStock(),
                inventory
        );
    }

    private AdminOrderResponse order(String orderNumber) {
        return jdbcTemplate.query("""
                        SELECT public_number, customer_name, customer_email,
                               status, payment_status, grand_total, created_at
                        FROM orders WHERE public_number = ?
                        """,
                (rs, rowNum) -> new AdminOrderResponse(
                        rs.getString("public_number"),
                        rs.getString("customer_name"),
                        rs.getString("customer_email"),
                        rs.getString("status"),
                        rs.getString("payment_status"),
                        rs.getBigDecimal("grand_total"),
                        rs.getTimestamp("created_at").toLocalDateTime()
                ),
                orderNumber
        ).stream().findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Pedido não encontrado"));
    }

    private void ensureCategory(Long categoryId) {
        Long matches = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM categories WHERE id = ? AND active = true",
                Long.class,
                categoryId
        );
        if (matches == null || matches != 1) {
            throw new ResourceNotFoundException("Categoria não encontrada");
        }
    }

    private void ensureProduct(Long productId) {
        Long matches = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM products WHERE id = ?",
                Long.class,
                productId
        );
        if (matches == null || matches != 1) {
            throw new ResourceNotFoundException("Produto não encontrado");
        }
    }

    private void audit(String email, String action, String entityType, Long entityId, String metadata) {
        jdbcTemplate.update("""
                INSERT INTO audit_logs (
                    actor_user_id, action, entity_type, entity_id, metadata_json
                ) VALUES (?, ?, ?, ?, ?)
                """,
                userId(email),
                action,
                entityType,
                entityId.toString(),
                metadata == null ? "{}" : "{\"detail\":\"" + jsonEscape(metadata) + "\"}"
        );
    }

    private Long userId(String email) {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM users WHERE lower(email) = lower(?)",
                Long.class,
                email
        );
    }

    private long count(String sql) {
        Long result = jdbcTemplate.queryForObject(sql, Long.class);
        return result == null ? 0 : result;
    }

    private BigDecimal amount(String sql) {
        BigDecimal result = jdbcTemplate.queryForObject(sql, BigDecimal.class);
        return result == null ? BigDecimal.ZERO : result;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String jsonEscape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private record VariantStock(int stock, int reserved) {
    }

    private record OrderState(Long id, String status, String shippingService, int estimatedDays) {
    }

    private record ImageState(Long productId, boolean mainImage) {
    }
}
