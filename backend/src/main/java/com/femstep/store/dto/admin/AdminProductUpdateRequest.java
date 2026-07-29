package com.femstep.store.dto.admin;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record AdminProductUpdateRequest(
        @NotBlank @Size(max = 255) String name,
        @NotBlank @Pattern(regexp = "[a-z0-9]+(?:-[a-z0-9]+)*") @Size(max = 255) String slug,
        @NotBlank String description,
        @Size(max = 500) String shortDescription,
        @NotNull @DecimalMin("0.01") BigDecimal price,
        @NotNull Long categoryId,
        boolean featured,
        @Size(max = 120) String material,
        @Size(max = 120) String brand
) {
}
