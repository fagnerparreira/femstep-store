package com.femstep.store.dto.admin;

import java.math.BigDecimal;
import java.util.List;

public record AdminProductResponse(
        Long id,
        String name,
        String slug,
        String category,
        BigDecimal price,
        boolean active,
        boolean featured,
        long variants,
        long totalStock,
        long reservedStock,
        List<AdminVariantResponse> inventory
) {
}
