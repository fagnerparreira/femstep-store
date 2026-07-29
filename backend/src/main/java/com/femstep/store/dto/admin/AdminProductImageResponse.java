package com.femstep.store.dto.admin;

public record AdminProductImageResponse(
        Long id,
        String url,
        String altText,
        int displayOrder,
        boolean mainImage
) {
}
