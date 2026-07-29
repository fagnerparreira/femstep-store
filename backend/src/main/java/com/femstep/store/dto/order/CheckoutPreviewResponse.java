package com.femstep.store.dto.order;

import java.math.BigDecimal;

public record CheckoutPreviewResponse(
        BigDecimal subtotal,
        BigDecimal discount,
        BigDecimal shipping,
        BigDecimal total,
        String shippingService,
        int estimatedDays,
        String appliedCoupon
) {
}
