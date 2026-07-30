package com.femstep.store.dto.catalog;

public record ProductImageResponse(
        Long id,
        String url,
        String altText,
        Integer displayOrder,
        Boolean mainImage
) {
}
