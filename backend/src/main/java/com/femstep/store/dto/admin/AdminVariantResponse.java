package com.femstep.store.dto.admin;

import java.math.BigDecimal;

public record AdminVariantResponse(
        Long id,
        String sku,
        int size,
        String color,
        BigDecimal priceOverride,
        int stock,
        int reserved,
        int available,
        boolean active
) {
}
