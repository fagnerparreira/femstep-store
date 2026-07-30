package com.femstep.store.dto.order;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record OrderResponse(
        String orderNumber,
        String status,
        String paymentStatus,
        String paymentMethod,
        BigDecimal subtotal,
        BigDecimal discount,
        BigDecimal shipping,
        BigDecimal total,
        String shippingService,
        int estimatedDays,
        String pixCode,
        LocalDateTime createdAt,
        List<OrderItemResponse> items
) {
}
