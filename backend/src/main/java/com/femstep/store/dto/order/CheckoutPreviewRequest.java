package com.femstep.store.dto.order;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

public record CheckoutPreviewRequest(
        @Valid ShippingAddressRequest address,
        @Size(max = 60) String couponCode
) {
}
