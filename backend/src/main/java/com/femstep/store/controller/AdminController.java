package com.femstep.store.controller;

import com.femstep.store.dto.admin.AdminDashboardResponse;
import com.femstep.store.dto.admin.AdminCouponRequest;
import com.femstep.store.dto.admin.AdminCouponResponse;
import com.femstep.store.dto.admin.AdminOrderResponse;
import com.femstep.store.dto.admin.AdminProductRequest;
import com.femstep.store.dto.admin.AdminProductResponse;
import com.femstep.store.service.AdminService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
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

@Validated
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final AdminService adminService;

    public AdminController(AdminService adminService) {
        this.adminService = adminService;
    }

    @GetMapping("/dashboard")
    public AdminDashboardResponse dashboard() {
        return adminService.dashboard();
    }

    @GetMapping("/products")
    public List<AdminProductResponse> products() {
        return adminService.products();
    }

    @PostMapping("/products")
    @ResponseStatus(HttpStatus.CREATED)
    public AdminProductResponse createProduct(
            Authentication authentication,
            @Valid @RequestBody AdminProductRequest request) {
        return adminService.createProduct(request, authentication.getName());
    }

    @PatchMapping("/products/{productId}/active")
    public AdminProductResponse toggleProduct(
            Authentication authentication,
            @PathVariable Long productId,
            @RequestBody ToggleProductRequest request) {
        return adminService.toggleProduct(productId, request.active(), authentication.getName());
    }

    @PatchMapping("/variants/{variantId}/stock")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void adjustStock(
            Authentication authentication,
            @PathVariable Long variantId,
            @Valid @RequestBody StockRequest request) {
        adminService.adjustStock(
                variantId,
                request.stockQuantity(),
                request.reason(),
                authentication.getName()
        );
    }

    @GetMapping("/orders")
    public List<AdminOrderResponse> orders() {
        return adminService.orders();
    }

    @PatchMapping("/orders/{orderNumber}/status")
    public AdminOrderResponse advanceOrder(
            Authentication authentication,
            @PathVariable String orderNumber,
            @Valid @RequestBody OrderStatusRequest request) {
        return adminService.advanceOrder(
                orderNumber,
                request.status(),
                request.trackingCode(),
                authentication.getName()
        );
    }

    @GetMapping("/coupons")
    public List<AdminCouponResponse> coupons() {
        return adminService.coupons();
    }

    @PostMapping("/coupons")
    @ResponseStatus(HttpStatus.CREATED)
    public AdminCouponResponse createCoupon(
            Authentication authentication,
            @Valid @RequestBody AdminCouponRequest request
    ) {
        return adminService.createCoupon(request, authentication.getName());
    }

    @PutMapping("/coupons/{couponId}")
    public AdminCouponResponse updateCoupon(
            Authentication authentication,
            @PathVariable Long couponId,
            @Valid @RequestBody AdminCouponRequest request
    ) {
        return adminService.updateCoupon(couponId, request, authentication.getName());
    }

    @PatchMapping("/coupons/{couponId}/active")
    public AdminCouponResponse toggleCoupon(
            Authentication authentication,
            @PathVariable Long couponId,
            @RequestBody ToggleProductRequest request
    ) {
        return adminService.toggleCoupon(couponId, request.active(), authentication.getName());
    }

    public record ToggleProductRequest(boolean active) {
    }

    public record StockRequest(@Min(0) int stockQuantity, String reason) {
    }

    public record OrderStatusRequest(
            @NotBlank @Pattern(regexp = "PROCESSING|SHIPPED|DELIVERED") String status,
            String trackingCode) {
    }
}
