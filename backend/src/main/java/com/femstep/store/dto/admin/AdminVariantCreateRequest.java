package com.femstep.store.dto.admin;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record AdminVariantCreateRequest(
        @NotBlank @Size(max = 255) String sku,
        @Min(25) @Max(50) int size,
        @NotBlank @Size(max = 255) String color,
        @Min(0) int stock,
        @DecimalMin("0.01") BigDecimal priceOverride
) {
}
