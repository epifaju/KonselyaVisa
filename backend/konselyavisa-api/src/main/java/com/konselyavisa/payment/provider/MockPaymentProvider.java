package com.konselyavisa.payment.provider;

import com.konselyavisa.common.exception.BusinessException;
import com.konselyavisa.payment.domain.PaymentStatus;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class MockPaymentProvider implements PaymentProvider {

    @Override
    public String code() {
        return PaymentProviderCodes.MOCK;
    }

    @Override
    public CheckoutSession createCheckout(PaymentOrder order) {
        String reference = "mock_" + order.paymentId();
        return new CheckoutSession(
                reference, "/api/v1/payments/webhooks/MOCK?ref=" + reference, PaymentStatus.PENDING);
    }

    @Override
    public PaymentStatusView verify(String providerReference) {
        if (providerReference == null || !providerReference.startsWith("mock_")) {
            throw BusinessException.notFound("error.payment.not_found");
        }
        return new PaymentStatusView(PaymentStatus.PENDING, providerReference);
    }

    @Override
    public ParsedWebhook handleWebhook(WebhookPayload payload) {
        Map<String, Object> json = payload.json() == null ? Map.of() : payload.json();
        Object reference = json.get("providerReference");
        Object key = json.get("idempotencyKey");
        if (!(reference instanceof String providerReference) || providerReference.isBlank()) {
            throw BusinessException.badRequest("error.payment.webhook_invalid");
        }
        if (!(key instanceof String idempotencyKey) || idempotencyKey.isBlank()) {
            throw BusinessException.badRequest("error.payment.idempotency_required");
        }
        boolean completed = "payment.completed".equals(json.get("event"));
        return new ParsedWebhook(providerReference, idempotencyKey, completed);
    }
}
