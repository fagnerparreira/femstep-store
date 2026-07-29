package com.femstep.store.dto.order;

import java.math.BigDecimal;

public record OrderItemResponse(
        String sku,
        String productName,
        String productSlug,
        String imageUrl,
        int size,
        String color,
        BigDecimal unitPrice,
        int quantity,
        BigDecimal lineTotal
) {
}
