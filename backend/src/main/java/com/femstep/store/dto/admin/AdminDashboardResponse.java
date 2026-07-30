package com.femstep.store.dto.admin;

import java.math.BigDecimal;

public record AdminDashboardResponse(
        long totalProducts,
        long activeProducts,
        long lowStockVariants,
        long totalOrders,
        long pendingOrders,
        BigDecimal approvedRevenue
) {
}
