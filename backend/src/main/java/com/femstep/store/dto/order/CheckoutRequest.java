package com.femstep.store.dto.order;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CheckoutRequest(
        @Valid ShippingAddressRequest address,
        @Size(max = 60) String couponCode,
        @NotBlank @Pattern(regexp = "PIX|SANDBOX_CARD") String paymentMethod,
        @NotBlank @Size(min = 8, max = 60) String idempotencyKey,
        @Size(max = 1000) String notes
) {
}
