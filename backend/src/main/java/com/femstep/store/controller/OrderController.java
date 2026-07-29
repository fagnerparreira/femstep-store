package com.femstep.store.controller;

import com.femstep.store.dto.order.CheckoutPreviewRequest;
import com.femstep.store.dto.order.CheckoutPreviewResponse;
import com.femstep.store.dto.order.CheckoutRequest;
import com.femstep.store.dto.order.OrderResponse;
import com.femstep.store.service.OrderService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping("/checkout/preview")
    public CheckoutPreviewResponse preview(
            Authentication authentication,
            @Valid @RequestBody CheckoutPreviewRequest request) {
        return orderService.preview(authentication.getName(), request);
    }

    @PostMapping("/orders")
    @ResponseStatus(HttpStatus.CREATED)
    public OrderResponse checkout(
            Authentication authentication,
            @Valid @RequestBody CheckoutRequest request) {
        return orderService.checkout(authentication.getName(), request);
    }

    @GetMapping("/orders")
    public List<OrderResponse> list(Authentication authentication) {
        return orderService.list(authentication.getName());
    }

    @GetMapping("/orders/{orderNumber}")
    public OrderResponse get(
            Authentication authentication,
            @PathVariable String orderNumber) {
        return orderService.get(authentication.getName(), orderNumber);
    }

    @PostMapping("/orders/{orderNumber}/sandbox-approve")
    public OrderResponse sandboxApprove(
            Authentication authentication,
            @PathVariable String orderNumber) {
        return orderService.sandboxApprove(authentication.getName(), orderNumber);
    }

    @PostMapping("/orders/{orderNumber}/cancel")
    public OrderResponse cancel(
            Authentication authentication,
            @PathVariable String orderNumber) {
        return orderService.cancelPending(authentication.getName(), orderNumber);
    }
}
