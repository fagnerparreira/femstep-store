package com.femstep.store.service;

import com.femstep.store.dto.cart.CartItemRequest;
import com.femstep.store.dto.cart.CartItemResponse;
import com.femstep.store.dto.cart.CartResponse;
import com.femstep.store.exception.BusinessRuleException;
import com.femstep.store.exception.ResourceNotFoundException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
public class CartService {

    private final JdbcTemplate jdbcTemplate;

    public CartService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public CartResponse getCart(String email) {
        return loadCart(getOrCreateCart(userId(email)));
    }

    @Transactional
    public CartResponse addItem(String email, CartItemRequest request) {
        Long cartId = getOrCreateCart(userId(email));
        VariantAvailability variant = lockVariant(request.variantId());
        Integer currentQuantity = jdbcTemplate.query(
                "SELECT quantity FROM cart_items WHERE cart_id = ? AND variant_id = ?",
                (rs, rowNum) -> rs.getInt("quantity"),
                cartId,
                request.variantId()
        ).stream().findFirst().orElse(0);

        int desiredQuantity = currentQuantity + request.quantity();
        validateQuantity(desiredQuantity, variant.availableStock());

        jdbcTemplate.update("""
                INSERT INTO cart_items (cart_id, variant_id, quantity)
                VALUES (?, ?, ?)
                ON CONFLICT (cart_id, variant_id)
                DO UPDATE SET quantity = EXCLUDED.quantity, updated_at = CURRENT_TIMESTAMP
                """, cartId, request.variantId(), desiredQuantity);
        return loadCart(cartId);
    }

    @Transactional
    public CartResponse updateItem(String email, Long itemId, int quantity) {
        if (quantity <= 0) {
            return removeItem(email, itemId);
        }
        if (quantity > 10) {
            throw new BusinessRuleException("A quantidade máxima por item é 10");
        }

        OwnedItem item = lockOwnedItem(email, itemId);
        VariantAvailability variant = lockVariant(item.variantId());
        validateQuantity(quantity, variant.availableStock());
        jdbcTemplate.update(
                "UPDATE cart_items SET quantity = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?",
                quantity,
                itemId
        );
        return loadCart(item.cartId());
    }

    @Transactional
    public CartResponse removeItem(String email, Long itemId) {
        OwnedItem item = lockOwnedItem(email, itemId);
        jdbcTemplate.update("DELETE FROM cart_items WHERE id = ?", itemId);
        return loadCart(item.cartId());
    }

    @Transactional
    public CartResponse clear(String email) {
        Long cartId = getOrCreateCart(userId(email));
        jdbcTemplate.update("DELETE FROM cart_items WHERE cart_id = ?", cartId);
        return loadCart(cartId);
    }

    private Long userId(String email) {
        return jdbcTemplate.query(
                "SELECT id FROM users WHERE lower(email) = lower(?) AND active = true",
                (rs, rowNum) -> rs.getLong("id"),
                email
        ).stream().findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado"));
    }

    private Long getOrCreateCart(Long userId) {
        return jdbcTemplate.query(
                "SELECT id FROM carts WHERE user_id = ? AND status = 'ACTIVE'",
                (rs, rowNum) -> rs.getLong("id"),
                userId
        ).stream().findFirst().orElseGet(() -> jdbcTemplate.queryForObject(
                "INSERT INTO carts (user_id, status) VALUES (?, 'ACTIVE') RETURNING id",
                Long.class,
                userId
        ));
    }

    private VariantAvailability lockVariant(Long variantId) {
        return jdbcTemplate.query("""
                        SELECT pv.id, GREATEST(pv.stock_quantity - pv.reserved_quantity, 0) AS available_stock
                        FROM product_variants pv
                        JOIN products p ON p.id = pv.product_id
                        WHERE pv.id = ? AND pv.active = true AND p.active = true
                        FOR UPDATE OF pv
                        """,
                (rs, rowNum) -> new VariantAvailability(
                        rs.getLong("id"),
                        rs.getInt("available_stock")
                ),
                variantId
        ).stream().findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Variação do produto não encontrada"));
    }

    private OwnedItem lockOwnedItem(String email, Long itemId) {
        return jdbcTemplate.query("""
                        SELECT ci.cart_id, ci.variant_id
                        FROM cart_items ci
                        JOIN carts c ON c.id = ci.cart_id
                        JOIN users u ON u.id = c.user_id
                        WHERE ci.id = ? AND lower(u.email) = lower(?) AND c.status = 'ACTIVE'
                        FOR UPDATE OF ci
                        """,
                (rs, rowNum) -> new OwnedItem(
                        rs.getLong("cart_id"),
                        rs.getLong("variant_id")
                ),
                itemId,
                email
        ).stream().findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Item do carrinho não encontrado"));
    }

    private CartResponse loadCart(Long cartId) {
        List<CartItemResponse> items = jdbcTemplate.query("""
                        SELECT ci.id,
                               p.id AS product_id,
                               pv.id AS variant_id,
                               p.name AS product_name,
                               p.slug AS product_slug,
                               COALESCE(
                                   (SELECT pi.url FROM product_images pi
                                    WHERE pi.product_id = p.id
                                    ORDER BY pi.main_image DESC, pi.display_order, pi.id
                                    LIMIT 1),
                                   ''
                               ) AS product_image,
                               COALESCE(pv.price_override, p.base_price) AS unit_price,
                               ci.quantity,
                               pv.size,
                               pv.color,
                               GREATEST(pv.stock_quantity - pv.reserved_quantity, 0) AS available_stock
                        FROM cart_items ci
                        JOIN product_variants pv ON pv.id = ci.variant_id
                        JOIN products p ON p.id = pv.product_id
                        WHERE ci.cart_id = ?
                        ORDER BY ci.created_at, ci.id
                        """,
                (rs, rowNum) -> {
                    BigDecimal price = rs.getBigDecimal("unit_price");
                    int quantity = rs.getInt("quantity");
                    return new CartItemResponse(
                            rs.getLong("id"),
                            rs.getLong("product_id"),
                            rs.getLong("variant_id"),
                            rs.getString("product_name"),
                            rs.getString("product_slug"),
                            rs.getString("product_image"),
                            price,
                            quantity,
                            rs.getInt("size"),
                            rs.getString("color"),
                            rs.getInt("available_stock"),
                            price.multiply(BigDecimal.valueOf(quantity))
                    );
                },
                cartId
        );
        BigDecimal subtotal = items.stream()
                .map(CartItemResponse::lineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        int totalItems = items.stream().mapToInt(CartItemResponse::quantity).sum();
        return new CartResponse(cartId, items, subtotal, totalItems);
    }

    private void validateQuantity(int requested, int available) {
        if (requested < 1 || requested > 10) {
            throw new BusinessRuleException("A quantidade deve ficar entre 1 e 10");
        }
        if (requested > available) {
            throw new BusinessRuleException("Estoque insuficiente para esta variação");
        }
    }

    private record VariantAvailability(Long id, int availableStock) {
    }

    private record OwnedItem(Long cartId, Long variantId) {
    }
}
