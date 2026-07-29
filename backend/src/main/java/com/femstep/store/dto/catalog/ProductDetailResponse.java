package com.femstep.store.dto.catalog;

import java.math.BigDecimal;
import java.util.List;

public record ProductDetailResponse(
        Long id,
        String name,
        String slug,
        String description,
        String shortDescription,
        BigDecimal price,
        BigDecimal compareAtPrice,
        String brand,
        String material,
        String category,
        String categorySlug,
        Boolean featured,
        String seoTitle,
        String seoDescription,
        List<ProductVariantResponse> variants,
        List<ProductImageResponse> images
) {
}
