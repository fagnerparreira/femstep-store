package com.femstep.store.dto.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record AdminCategoryRequest(
        @NotBlank @Size(max = 255) String name,
        @NotBlank @Size(max = 255)
        @Pattern(regexp = "[a-z0-9]+(?:-[a-z0-9]+)*") String slug,
        @Size(max = 1000) String description,
        boolean active
) {
}
