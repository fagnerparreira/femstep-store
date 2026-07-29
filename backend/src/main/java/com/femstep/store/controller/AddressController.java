package com.femstep.store.controller;

import com.femstep.store.dto.address.AddressRequest;
import com.femstep.store.dto.address.AddressResponse;
import com.femstep.store.service.AddressService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/account/addresses")
public class AddressController {

    private final AddressService addressService;

    public AddressController(AddressService addressService) {
        this.addressService = addressService;
    }

    @GetMapping
    public List<AddressResponse> list(Authentication authentication) {
        return addressService.list(authentication.getName());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AddressResponse create(
            Authentication authentication,
            @Valid @RequestBody AddressRequest request) {
        return addressService.create(authentication.getName(), request);
    }

    @PutMapping("/{addressId}")
    public AddressResponse update(
            Authentication authentication,
            @PathVariable Long addressId,
            @Valid @RequestBody AddressRequest request) {
        return addressService.update(authentication.getName(), addressId, request);
    }

    @DeleteMapping("/{addressId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(Authentication authentication, @PathVariable Long addressId) {
        addressService.delete(authentication.getName(), addressId);
    }

    @PatchMapping("/{addressId}/default")
    public AddressResponse makeDefault(
            Authentication authentication,
            @PathVariable Long addressId) {
        return addressService.makeDefault(authentication.getName(), addressId);
    }
}
