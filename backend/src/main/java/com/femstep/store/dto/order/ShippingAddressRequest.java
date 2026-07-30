package com.femstep.store.dto.order;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ShippingAddressRequest(
        @NotBlank @Size(max = 255) String recipientName,
        @NotBlank @Pattern(regexp = "\\d{8}") String postalCode,
        @NotBlank @Size(max = 255) String street,
        @NotBlank @Size(max = 40) String number,
        @Size(max = 255) String complement,
        @NotBlank @Size(max = 160) String neighborhood,
        @NotBlank @Size(max = 160) String city,
        @NotBlank @Pattern(regexp = "[A-Za-z]{2}") String state
) {
}
