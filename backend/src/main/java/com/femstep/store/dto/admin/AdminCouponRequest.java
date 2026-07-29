package com.femstep.store.dto.admin;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record AdminCouponRequest(
        @NotBlank @Size(max = 60) @Pattern(regexp = "^[A-Za-z0-9_-]+$") String code,
        @Size(max = 255) String description,
        @NotBlank @Pattern(regexp = "PERCENTAGE|FIXED") String discountType,
        @NotNull @DecimalMin("0.01") BigDecimal discountValue,
        @NotNull @DecimalMin("0.00") BigDecimal minimumOrderValue,
        @DecimalMin("0.01") BigDecimal maximumDiscountValue,
        @Min(1) Integer usageLimit,
        @NotNull @Min(1) Integer usageLimitPerUser,
        @NotNull LocalDateTime startsAt,
        @NotNull @Future LocalDateTime endsAt,
        boolean active
) {
}
