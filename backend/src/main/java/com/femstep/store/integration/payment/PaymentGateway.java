package com.femstep.store.integration.payment;

import java.math.BigDecimal;

public interface PaymentGateway {
    PaymentIntent create(String orderNumber, String method, BigDecimal amount, String idempotencyKey);

    void requireManualApproval();

    record PaymentIntent(
            String provider,
            String method,
            Integer installments,
            String pixCode,
            boolean autoApprove
    ) {
    }
}
