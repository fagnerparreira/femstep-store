package com.femstep.store.integration.shipping;

import com.femstep.store.dto.order.ShippingAddressRequest;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class FixedShippingGateway implements ShippingGateway {
    private static final BigDecimal FREE_SHIPPING_THRESHOLD = new BigDecimal("299.00");

    @Override
    public ShippingQuote quote(BigDecimal subtotal, ShippingAddressRequest address) {
        BigDecimal price = subtotal.compareTo(FREE_SHIPPING_THRESHOLD) >= 0
                ? BigDecimal.ZERO
                : address.state().equalsIgnoreCase("SP")
                    ? new BigDecimal("19.90")
                    : new BigDecimal("29.90");
        int days = address.state().equalsIgnoreCase("SP") ? 4 : 7;
        String service = price.signum() == 0
                ? "Entrega econômica grátis"
                : "Entrega econômica";
        return new ShippingQuote(price, service, days);
    }
}
