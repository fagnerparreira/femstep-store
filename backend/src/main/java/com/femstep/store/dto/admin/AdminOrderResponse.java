package com.femstep.store.dto.admin;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record AdminOrderResponse(
        String orderNumber,
        String customerName,
        String customerEmail,
        String status,
        String paymentStatus,
        BigDecimal total,
        LocalDateTime createdAt
) {
}
