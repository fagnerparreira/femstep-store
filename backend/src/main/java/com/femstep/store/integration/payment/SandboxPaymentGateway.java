package com.femstep.store.integration.payment;

import com.femstep.store.exception.BusinessRuleException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class SandboxPaymentGateway implements PaymentGateway {
    private final boolean enabled;

    public SandboxPaymentGateway(@Value("${payments.sandbox-enabled:false}") boolean enabled) {
        this.enabled = enabled;
    }

    @Override
    public PaymentIntent create(
            String orderNumber,
            String method,
            BigDecimal amount,
            String idempotencyKey
    ) {
        requireManualApproval();
        return new PaymentIntent(
                "SANDBOX",
                method,
                "SANDBOX_CARD".equals(method) ? 1 : null,
                "PIX".equals(method) ? "PIX-SANDBOX-" + orderNumber : null,
                "SANDBOX_CARD".equals(method)
        );
    }

    @Override
    public void requireManualApproval() {
        if (!enabled) {
            throw new BusinessRuleException(
                    "O pagamento sandbox está desativado. Configure um provedor de pagamentos."
            );
        }
    }
}
