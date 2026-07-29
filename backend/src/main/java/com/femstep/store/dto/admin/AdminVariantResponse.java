package com.femstep.store.dto.admin;

public record AdminVariantResponse(
        Long id,
        String sku,
        int size,
        String color,
        int stock,
        int reserved,
        int available,
        boolean active
) {
}
