package com.femstep.store.dto.admin;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

public record AdminProductRequest(
        @NotBlank @Size(max = 255) String name,
        @NotBlank @Pattern(regexp = "[a-z0-9]+(?:-[a-z0-9]+)*") @Size(max = 255) String slug,
        @NotBlank String description,
        @Size(max = 500) String shortDescription,
        @NotNull @DecimalMin("0.01") BigDecimal price,
        @NotNull Long categoryId,
        boolean featured,
        @Size(max = 120) String material,
        @Size(max = 120) String brand,
        @NotBlank @Size(max = 500) String imageUrl,
        @NotEmpty List<@Valid VariantRequest> variants
) {
    public record VariantRequest(
            @NotBlank @Size(max = 255) String sku,
            @Min(25) @Max(50) int size,
            @NotBlank @Size(max = 255) String color,
            @Min(0) int stock
    ) {
    }
}
