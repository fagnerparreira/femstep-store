package com.femstep.store.service;

import com.femstep.store.dto.order.CheckoutPreviewRequest;
import com.femstep.store.dto.order.CheckoutPreviewResponse;
import com.femstep.store.dto.order.CheckoutRequest;
import com.femstep.store.dto.order.OrderItemResponse;
import com.femstep.store.dto.order.OrderResponse;
import com.femstep.store.dto.order.ShippingAddressRequest;
import com.femstep.store.exception.BusinessRuleException;
import com.femstep.store.exception.ResourceNotFoundException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class OrderService {

    private static final BigDecimal FREE_SHIPPING_THRESHOLD = new BigDecimal("299.00");

    private final JdbcTemplate jdbcTemplate;
    private final boolean sandboxEnabled;

    public OrderService(
            JdbcTemplate jdbcTemplate,
            @Value("${payments.sandbox-enabled:false}") boolean sandboxEnabled) {
        this.jdbcTemplate = jdbcTemplate;
        this.sandboxEnabled = sandboxEnabled;
    }

    @Transactional(readOnly = true)
    public CheckoutPreviewResponse preview(String email, CheckoutPreviewRequest request) {
        UserSnapshot user = user(email);
        CartSnapshot cart = cart(user.id(), false);
        Pricing pricing = pricing(user.id(), cart.subtotal(), request.couponCode(), request.address());
        return new CheckoutPreviewResponse(
                cart.subtotal(),
                pricing.discount(),
                pricing.shipping(),
                pricing.total(),
                pricing.service(),
                pricing.estimatedDays(),
                pricing.coupon() == null ? null : pricing.coupon().code()
        );
    }

    @Transactional
    public OrderResponse checkout(String email, CheckoutRequest request) {
        UserSnapshot user = user(email);
        List<Long> existingOrder = jdbcTemplate.query(
                "SELECT id FROM orders WHERE user_id = ? AND idempotency_key = ?",
                (rs, rowNum) -> rs.getLong("id"),
                user.id(),
                request.idempotencyKey()
        );
        if (!existingOrder.isEmpty()) {
            return orderById(user.id(), existingOrder.getFirst());
        }

        CartSnapshot cart = cart(user.id(), true);
        Pricing pricing = pricing(user.id(), cart.subtotal(), request.couponCode(), request.address());
        String orderNumber = newOrderNumber();
        ShippingAddressRequest address = request.address();
        String state = address.state().toUpperCase(Locale.ROOT);

        Long orderId = jdbcTemplate.queryForObject("""
                INSERT INTO orders (
                    public_number, user_id, status, payment_status,
                    customer_email, customer_name, customer_document,
                    recipient_name, shipping_postal_code, shipping_street,
                    shipping_number, shipping_complement, shipping_neighborhood,
                    shipping_city, shipping_state, shipping_service,
                    shipping_estimated_days, subtotal, discount_total,
                    shipping_total, grand_total, coupon_id, idempotency_key, notes
                ) VALUES (
                    ?, ?, 'PENDING_PAYMENT', 'PENDING',
                    ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?
                ) RETURNING id
                """,
                Long.class,
                orderNumber,
                user.id(),
                user.email(),
                user.fullName(),
                user.document(),
                address.recipientName(),
                address.postalCode(),
                address.street(),
                address.number(),
                blankToNull(address.complement()),
                address.neighborhood(),
                address.city(),
                state,
                pricing.service(),
                pricing.estimatedDays(),
                cart.subtotal(),
                pricing.discount(),
                pricing.shipping(),
                pricing.total(),
                pricing.coupon() == null ? null : pricing.coupon().id(),
                request.idempotencyKey(),
                blankToNull(request.notes())
        );

        for (CheckoutItem item : cart.items()) {
            if (item.quantity() > item.availableStock()) {
                throw new BusinessRuleException(
                        "O estoque de " + item.productName() + " mudou. Revise o carrinho."
                );
            }
            jdbcTemplate.update("""
                    INSERT INTO order_items (
                        order_id, product_id, variant_id, sku, product_name,
                        product_slug, image_url, size, color, unit_price,
                        quantity, line_total
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    orderId,
                    item.productId(),
                    item.variantId(),
                    item.sku(),
                    item.productName(),
                    item.productSlug(),
                    item.imageUrl(),
                    item.size(),
                    item.color(),
                    item.unitPrice(),
                    item.quantity(),
                    item.lineTotal()
            );
            int updated = jdbcTemplate.update("""
                    UPDATE product_variants
                    SET reserved_quantity = reserved_quantity + ?
                    WHERE id = ?
                      AND active = true
                      AND stock_quantity - reserved_quantity >= ?
                    """, item.quantity(), item.variantId(), item.quantity());
            if (updated != 1) {
                throw new BusinessRuleException(
                        "O estoque de " + item.productName() + " mudou. Revise o carrinho."
                );
            }
            jdbcTemplate.update("""
                    INSERT INTO stock_reservations (
                        order_id, variant_id, quantity, status, expires_at
                    ) VALUES (?, ?, ?, 'ACTIVE', CURRENT_TIMESTAMP + INTERVAL '30 minutes')
                    """, orderId, item.variantId(), item.quantity());
        }

        jdbcTemplate.update("""
                INSERT INTO order_status_history (
                    order_id, previous_status, new_status, reason, changed_by_user_id
                ) VALUES (?, NULL, 'PENDING_PAYMENT', 'Pedido criado pelo cliente', ?)
                """, orderId, user.id());

        if (pricing.coupon() != null && pricing.discount().signum() > 0) {
            jdbcTemplate.update("""
                    INSERT INTO coupon_redemptions (
                        coupon_id, user_id, order_id, discount_amount
                    ) VALUES (?, ?, ?, ?)
                    """, pricing.coupon().id(), user.id(), orderId, pricing.discount());
        }

        String pixCode = request.paymentMethod().equals("PIX")
                ? "00020126580014BR.GOV.BCB.PIX0136" + orderNumber + "5204000053039865802BR5907FEMSTEP6009SAOPAULO62070503***6304DEMO"
                : null;
        jdbcTemplate.update("""
                INSERT INTO payments (
                    order_id, provider, method, status, amount,
                    installments, idempotency_key, pix_qr_code, expires_at
                ) VALUES (?, 'SANDBOX', ?, 'PENDING', ?, ?, ?, ?, CURRENT_TIMESTAMP + INTERVAL '30 minutes')
                """,
                orderId,
                request.paymentMethod(),
                pricing.total(),
                request.paymentMethod().equals("SANDBOX_CARD") ? 1 : null,
                "pay-" + request.idempotencyKey(),
                pixCode
        );

        jdbcTemplate.update(
                "UPDATE carts SET status = 'CONVERTED', updated_at = CURRENT_TIMESTAMP WHERE id = ?",
                cart.id()
        );

        if (request.paymentMethod().equals("SANDBOX_CARD")) {
            requireSandbox();
            approvePayment(user.id(), orderId);
        }
        return orderById(user.id(), orderId);
    }

    @Transactional(readOnly = true)
    public List<OrderResponse> list(String email) {
        Long userId = user(email).id();
        List<Long> ids = jdbcTemplate.query(
                "SELECT id FROM orders WHERE user_id = ? ORDER BY created_at DESC",
                (rs, rowNum) -> rs.getLong("id"),
                userId
        );
        return ids.stream().map(id -> orderById(userId, id)).toList();
    }

    @Transactional(readOnly = true)
    public OrderResponse get(String email, String orderNumber) {
        Long userId = user(email).id();
        Long orderId = jdbcTemplate.query(
                "SELECT id FROM orders WHERE user_id = ? AND public_number = ?",
                (rs, rowNum) -> rs.getLong("id"),
                userId,
                orderNumber
        ).stream().findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Pedido não encontrado"));
        return orderById(userId, orderId);
    }

    @Transactional
    public OrderResponse sandboxApprove(String email, String orderNumber) {
        requireSandbox();
        Long userId = user(email).id();
        Long orderId = jdbcTemplate.query("""
                        SELECT id FROM orders
                        WHERE user_id = ? AND public_number = ? AND status = 'PENDING_PAYMENT'
                        FOR UPDATE
                        """,
                (rs, rowNum) -> rs.getLong("id"),
                userId,
                orderNumber
        ).stream().findFirst()
                .orElseThrow(() -> new BusinessRuleException("Este pedido não pode mais ser aprovado"));
        approvePayment(userId, orderId);
        return orderById(userId, orderId);
    }

    @Transactional
    public OrderResponse cancelPending(String email, String orderNumber) {
        Long userId = user(email).id();
        Long orderId = jdbcTemplate.query("""
                        SELECT id FROM orders
                        WHERE user_id = ? AND public_number = ? AND status = 'PENDING_PAYMENT'
                        FOR UPDATE
                        """,
                (rs, rowNum) -> rs.getLong("id"),
                userId,
                orderNumber
        ).stream().findFirst()
                .orElseThrow(() -> new BusinessRuleException("Somente pedidos aguardando pagamento podem ser cancelados"));
        List<ReservedItem> reservations = jdbcTemplate.query("""
                        SELECT sr.variant_id, sr.quantity, pv.stock_quantity, pv.reserved_quantity
                        FROM stock_reservations sr
                        JOIN product_variants pv ON pv.id = sr.variant_id
                        WHERE sr.order_id = ? AND sr.status = 'ACTIVE'
                        FOR UPDATE OF sr, pv
                        """,
                (rs, rowNum) -> new ReservedItem(
                        rs.getLong("variant_id"),
                        rs.getInt("quantity"),
                        rs.getInt("stock_quantity"),
                        rs.getInt("reserved_quantity")
                ),
                orderId
        );
        for (ReservedItem reservation : reservations) {
            jdbcTemplate.update("""
                    UPDATE product_variants
                    SET reserved_quantity = GREATEST(reserved_quantity - ?, 0)
                    WHERE id = ?
                    """, reservation.quantity(), reservation.variantId());
        }
        jdbcTemplate.update("""
                UPDATE stock_reservations
                SET status = 'RELEASED', released_at = CURRENT_TIMESTAMP
                WHERE order_id = ? AND status = 'ACTIVE'
                """, orderId);
        jdbcTemplate.update("""
                UPDATE payments SET status = 'CANCELLED', updated_at = CURRENT_TIMESTAMP
                WHERE order_id = ? AND status = 'PENDING'
                """, orderId);
        jdbcTemplate.update(
                "DELETE FROM coupon_redemptions WHERE order_id = ?",
                orderId
        );
        jdbcTemplate.update("""
                UPDATE orders
                SET status = 'CANCELLED', payment_status = 'CANCELLED',
                    cancelled_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP
                WHERE id = ?
                """, orderId);
        jdbcTemplate.update("""
                INSERT INTO order_status_history (
                    order_id, previous_status, new_status, reason, changed_by_user_id
                ) VALUES (?, 'PENDING_PAYMENT', 'CANCELLED', 'Cancelado pelo cliente', ?)
                """, orderId, userId);
        return orderById(userId, orderId);
    }

    private void approvePayment(Long userId, Long orderId) {
        List<ReservedItem> items = jdbcTemplate.query("""
                        SELECT sr.variant_id, sr.quantity, pv.stock_quantity, pv.reserved_quantity
                        FROM stock_reservations sr
                        JOIN product_variants pv ON pv.id = sr.variant_id
                        WHERE sr.order_id = ?
                          AND sr.status = 'ACTIVE'
                          AND sr.expires_at > CURRENT_TIMESTAMP
                        FOR UPDATE OF pv, sr
                        """,
                (rs, rowNum) -> new ReservedItem(
                        rs.getLong("variant_id"),
                        rs.getInt("quantity"),
                        rs.getInt("stock_quantity"),
                        rs.getInt("reserved_quantity")
                ),
                orderId
        );
        if (items.isEmpty()) {
            throw new BusinessRuleException("A reserva de estoque não está mais ativa");
        }
        for (ReservedItem item : items) {
            int stockAfter = item.stockBefore() - item.quantity();
            if (stockAfter < 0 || item.reservedBefore() < item.quantity()) {
                throw new BusinessRuleException("Não foi possível confirmar o estoque do pedido");
            }
            jdbcTemplate.update("""
                    UPDATE product_variants
                    SET stock_quantity = stock_quantity - ?,
                        reserved_quantity = reserved_quantity - ?
                    WHERE id = ?
                    """, item.quantity(), item.quantity(), item.variantId());
            jdbcTemplate.update("""
                    INSERT INTO inventory_movements (
                        variant_id, order_id, movement_type, quantity,
                        stock_before, stock_after, reason, created_by_user_id
                    ) VALUES (?, ?, 'SALE', ?, ?, ?, 'Pagamento aprovado', ?)
                    """,
                    item.variantId(),
                    orderId,
                    -item.quantity(),
                    item.stockBefore(),
                    stockAfter,
                    userId
            );
        }
        jdbcTemplate.update("""
                UPDATE stock_reservations
                SET status = 'CONFIRMED', released_at = CURRENT_TIMESTAMP
                WHERE order_id = ? AND status = 'ACTIVE'
                """, orderId);
        jdbcTemplate.update("""
                UPDATE payments
                SET status = 'APPROVED', approved_at = CURRENT_TIMESTAMP,
                    updated_at = CURRENT_TIMESTAMP
                WHERE order_id = ? AND status = 'PENDING'
                """, orderId);
        jdbcTemplate.update("""
                UPDATE orders
                SET status = 'PAID', payment_status = 'APPROVED',
                    paid_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP
                WHERE id = ?
                """, orderId);
        jdbcTemplate.update("""
                INSERT INTO order_status_history (
                    order_id, previous_status, new_status, reason, changed_by_user_id
                ) VALUES (?, 'PENDING_PAYMENT', 'PAID', 'Pagamento aprovado no ambiente sandbox', ?)
                """, orderId, userId);
    }

    private CartSnapshot cart(Long userId, boolean lockItems) {
        Long cartId = jdbcTemplate.query(
                "SELECT id FROM carts WHERE user_id = ? AND status = 'ACTIVE'",
                (rs, rowNum) -> rs.getLong("id"),
                userId
        ).stream().findFirst()
                .orElseThrow(() -> new BusinessRuleException("Seu carrinho está vazio"));
        String lockClause = lockItems ? " FOR UPDATE OF pv" : "";
        List<CheckoutItem> items = jdbcTemplate.query("""
                        SELECT p.id AS product_id,
                               pv.id AS variant_id,
                               pv.sku,
                               p.name AS product_name,
                               p.slug AS product_slug,
                               COALESCE(
                                   (SELECT pi.url FROM product_images pi
                                    WHERE pi.product_id = p.id
                                    ORDER BY pi.main_image DESC, pi.display_order, pi.id
                                    LIMIT 1),
                                   ''
                               ) AS image_url,
                               pv.size,
                               pv.color,
                               COALESCE(pv.price_override, p.base_price) AS unit_price,
                               ci.quantity,
                               GREATEST(pv.stock_quantity - pv.reserved_quantity, 0) AS available_stock
                        FROM cart_items ci
                        JOIN product_variants pv ON pv.id = ci.variant_id
                        JOIN products p ON p.id = pv.product_id
                        WHERE ci.cart_id = ? AND pv.active = true AND p.active = true
                        """ + lockClause,
                (rs, rowNum) -> {
                    BigDecimal unitPrice = rs.getBigDecimal("unit_price");
                    int quantity = rs.getInt("quantity");
                    return new CheckoutItem(
                            rs.getLong("product_id"),
                            rs.getLong("variant_id"),
                            rs.getString("sku"),
                            rs.getString("product_name"),
                            rs.getString("product_slug"),
                            rs.getString("image_url"),
                            rs.getInt("size"),
                            rs.getString("color"),
                            unitPrice,
                            quantity,
                            rs.getInt("available_stock"),
                            unitPrice.multiply(BigDecimal.valueOf(quantity))
                    );
                },
                cartId
        );
        if (items.isEmpty()) {
            throw new BusinessRuleException("Seu carrinho está vazio");
        }
        BigDecimal subtotal = items.stream()
                .map(CheckoutItem::lineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new CartSnapshot(cartId, items, subtotal);
    }

    private Pricing pricing(
            Long userId,
            BigDecimal subtotal,
            String couponCode,
            ShippingAddressRequest address) {
        if (address == null) {
            throw new BusinessRuleException("Informe o endereço de entrega");
        }
        Coupon coupon = coupon(userId, couponCode, subtotal);
        BigDecimal discount = coupon == null ? BigDecimal.ZERO : coupon.discount(subtotal);
        BigDecimal shipping = subtotal.compareTo(FREE_SHIPPING_THRESHOLD) >= 0
                ? BigDecimal.ZERO
                : address.state().equalsIgnoreCase("SP")
                    ? new BigDecimal("19.90")
                    : new BigDecimal("29.90");
        int days = address.state().equalsIgnoreCase("SP") ? 4 : 7;
        String service = shipping.signum() == 0 ? "Entrega econômica grátis" : "Entrega econômica";
        return new Pricing(
                coupon,
                discount,
                shipping,
                subtotal.subtract(discount).add(shipping).max(BigDecimal.ZERO),
                service,
                days
        );
    }

    private Coupon coupon(Long userId, String code, BigDecimal subtotal) {
        if (code == null || code.isBlank()) {
            return null;
        }
        Coupon coupon = jdbcTemplate.query("""
                        SELECT id, code, discount_type, discount_value,
                               minimum_order_value, maximum_discount_value,
                               usage_limit, usage_limit_per_user
                        FROM coupons
                        WHERE upper(code) = upper(?)
                          AND active = true
                          AND starts_at <= CURRENT_TIMESTAMP
                          AND ends_at >= CURRENT_TIMESTAMP
                        """,
                (rs, rowNum) -> new Coupon(
                        rs.getLong("id"),
                        rs.getString("code"),
                        rs.getString("discount_type"),
                        rs.getBigDecimal("discount_value"),
                        rs.getBigDecimal("minimum_order_value"),
                        rs.getBigDecimal("maximum_discount_value"),
                        (Integer) rs.getObject("usage_limit"),
                        rs.getInt("usage_limit_per_user")
                ),
                code.trim()
        ).stream().findFirst()
                .orElseThrow(() -> new BusinessRuleException("Cupom inválido ou expirado"));
        if (subtotal.compareTo(coupon.minimumOrder()) < 0) {
            throw new BusinessRuleException("O carrinho não atingiu o valor mínimo deste cupom");
        }
        Integer totalUses = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM coupon_redemptions WHERE coupon_id = ?",
                Integer.class,
                coupon.id()
        );
        Integer userUses = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM coupon_redemptions WHERE coupon_id = ? AND user_id = ?",
                Integer.class,
                coupon.id(),
                userId
        );
        if ((coupon.usageLimit() != null && totalUses >= coupon.usageLimit())
                || userUses >= coupon.perUserLimit()) {
            throw new BusinessRuleException("O limite de uso deste cupom foi atingido");
        }
        return coupon;
    }

    private UserSnapshot user(String email) {
        return jdbcTemplate.query("""
                        SELECT id, email, first_name, last_name, cpf
                        FROM users
                        WHERE lower(email) = lower(?) AND active = true
                        """,
                (rs, rowNum) -> new UserSnapshot(
                        rs.getLong("id"),
                        rs.getString("email"),
                        rs.getString("first_name") + " " + rs.getString("last_name"),
                        rs.getString("cpf")
                ),
                email
        ).stream().findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado"));
    }

    private OrderResponse orderById(Long userId, Long orderId) {
        OrderHeader header = jdbcTemplate.query("""
                        SELECT o.public_number, o.status, o.payment_status,
                               o.subtotal, o.discount_total, o.shipping_total,
                               o.grand_total, o.shipping_service,
                               o.shipping_estimated_days, o.created_at,
                               p.method, p.pix_qr_code
                        FROM orders o
                        LEFT JOIN payments p ON p.order_id = o.id
                        WHERE o.id = ? AND o.user_id = ?
                        ORDER BY p.id DESC
                        LIMIT 1
                        """,
                (rs, rowNum) -> new OrderHeader(
                        rs.getString("public_number"),
                        rs.getString("status"),
                        rs.getString("payment_status"),
                        rs.getString("method"),
                        rs.getBigDecimal("subtotal"),
                        rs.getBigDecimal("discount_total"),
                        rs.getBigDecimal("shipping_total"),
                        rs.getBigDecimal("grand_total"),
                        rs.getString("shipping_service"),
                        rs.getInt("shipping_estimated_days"),
                        rs.getString("pix_qr_code"),
                        rs.getTimestamp("created_at").toLocalDateTime()
                ),
                orderId,
                userId
        ).stream().findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Pedido não encontrado"));
        List<OrderItemResponse> items = jdbcTemplate.query("""
                        SELECT sku, product_name, product_slug, image_url,
                               size, color, unit_price, quantity, line_total
                        FROM order_items
                        WHERE order_id = ?
                        ORDER BY id
                        """,
                (rs, rowNum) -> new OrderItemResponse(
                        rs.getString("sku"),
                        rs.getString("product_name"),
                        rs.getString("product_slug"),
                        rs.getString("image_url"),
                        rs.getInt("size"),
                        rs.getString("color"),
                        rs.getBigDecimal("unit_price"),
                        rs.getInt("quantity"),
                        rs.getBigDecimal("line_total")
                ),
                orderId
        );
        return new OrderResponse(
                header.orderNumber(),
                header.status(),
                header.paymentStatus(),
                header.paymentMethod(),
                header.subtotal(),
                header.discount(),
                header.shipping(),
                header.total(),
                header.shippingService(),
                header.estimatedDays(),
                header.pixCode(),
                header.createdAt(),
                items
        );
    }

    private void requireSandbox() {
        if (!sandboxEnabled) {
            throw new BusinessRuleException(
                    "O pagamento sandbox está desativado. Configure um provedor de pagamentos."
            );
        }
    }

    private String newOrderNumber() {
        return "FS"
                + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"))
                + "-"
                + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private record UserSnapshot(Long id, String email, String fullName, String document) {
    }

    private record CheckoutItem(
            Long productId,
            Long variantId,
            String sku,
            String productName,
            String productSlug,
            String imageUrl,
            int size,
            String color,
            BigDecimal unitPrice,
            int quantity,
            int availableStock,
            BigDecimal lineTotal) {
    }

    private record CartSnapshot(Long id, List<CheckoutItem> items, BigDecimal subtotal) {
    }

    private record Pricing(
            Coupon coupon,
            BigDecimal discount,
            BigDecimal shipping,
            BigDecimal total,
            String service,
            int estimatedDays) {
    }

    private record Coupon(
            Long id,
            String code,
            String type,
            BigDecimal value,
            BigDecimal minimumOrder,
            BigDecimal maximumDiscount,
            Integer usageLimit,
            int perUserLimit) {

        private BigDecimal discount(BigDecimal subtotal) {
            BigDecimal result = type.equals("PERCENTAGE")
                    ? subtotal.multiply(value).divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP)
                    : value;
            if (maximumDiscount != null) {
                result = result.min(maximumDiscount);
            }
            return result.min(subtotal);
        }
    }

    private record ReservedItem(
            Long variantId,
            int quantity,
            int stockBefore,
            int reservedBefore) {
    }

    private record OrderHeader(
            String orderNumber,
            String status,
            String paymentStatus,
            String paymentMethod,
            BigDecimal subtotal,
            BigDecimal discount,
            BigDecimal shipping,
            BigDecimal total,
            String shippingService,
            int estimatedDays,
            String pixCode,
            LocalDateTime createdAt) {
    }
}
