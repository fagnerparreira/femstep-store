package com.femstep.store.dto.admin;

public record AdminCategoryResponse(
        Long id,
        String name,
        String slug,
        String description,
        boolean active,
        long products
) {
}
