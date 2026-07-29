package com.femstep.store.dto.cart;

import java.math.BigDecimal;

public record CartItemResponse(
        Long id,
        Long productId,
        Long variantId,
        String productName,
        String productSlug,
        String productImage,
        BigDecimal price,
        int quantity,
        int size,
        String color,
        int availableStock,
        BigDecimal lineTotal
) {
}
