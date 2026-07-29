package com.femstep.store.dto.admin;

import java.math.BigDecimal;

public record AdminProductDetailsResponse(
        Long id,
        String name,
        String slug,
        String description,
        String shortDescription,
        BigDecimal price,
        Long categoryId,
        boolean active,
        boolean featured,
        String material,
        String brand
) {
}
