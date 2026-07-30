package com.femstep.store.dto.catalog;

import java.math.BigDecimal;
import java.util.List;

public record ProductSummaryResponse(
        Long id,
        String name,
        String slug,
        String shortDescription,
        BigDecimal price,
        BigDecimal compareAtPrice,
        String imageUrl,
        String imageAlt,
        String category,
        String categorySlug,
        String material,
        Boolean featured,
        Boolean onSale,
        List<Integer> availableSizes,
        List<String> availableColors,
        Integer availableStock
) {
}
