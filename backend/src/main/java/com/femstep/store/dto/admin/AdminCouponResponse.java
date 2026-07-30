package com.femstep.store.dto.admin;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record AdminCouponResponse(
        Long id,
        String code,
        String description,
        String discountType,
        BigDecimal discountValue,
        BigDecimal minimumOrderValue,
        BigDecimal maximumDiscountValue,
        Integer usageLimit,
        Integer usageLimitPerUser,
        long uses,
        LocalDateTime startsAt,
        LocalDateTime endsAt,
        boolean active
) {
}
