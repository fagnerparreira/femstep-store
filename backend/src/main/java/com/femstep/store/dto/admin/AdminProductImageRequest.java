package com.femstep.store.dto.admin;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record AdminProductImageRequest(
        @NotBlank @Size(max = 500)
        @Pattern(regexp = "^(https://\\S+|/assets/\\S+)$") String url,
        @NotBlank @Size(max = 255) String altText,
        @Min(0) int displayOrder,
        boolean mainImage
) {
}
