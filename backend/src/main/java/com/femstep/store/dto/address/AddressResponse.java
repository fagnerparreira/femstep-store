package com.femstep.store.dto.address;

public record AddressResponse(
        Long id,
        String label,
        String recipientName,
        String postalCode,
        String street,
        String number,
        String complement,
        String neighborhood,
        String city,
        String state,
        String phone,
        boolean defaultAddress
) {
}
