package com.femstep.store.dto.catalog;

import java.math.BigDecimal;

public record ProductVariantResponse(
        Long id,
        String sku,
        Integer size,
        String color,
        Integer availableStock,
        BigDecimal price,
        Boolean active
) {
}
