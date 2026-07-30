package com.femstep.store.dto.catalog;

public record CategoryResponse(
        Long id,
        String name,
        String slug,
        String description
) {
}
