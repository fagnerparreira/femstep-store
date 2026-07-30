package com.femstep.store.integration.shipping;

import com.femstep.store.dto.order.ShippingAddressRequest;

import java.math.BigDecimal;

public interface ShippingGateway {
    ShippingQuote quote(BigDecimal subtotal, ShippingAddressRequest address);

    record ShippingQuote(BigDecimal price, String service, int estimatedDays) {
    }
}
