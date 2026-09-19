package com.konselyavisa.payment.provider;

import com.konselyavisa.common.exception.BusinessException;
import com.konselyavisa.payment.domain.PaymentStatus;
import org.springframework.stereotype.Component;

@Component
public class ManualPaymentProvider implements PaymentProvider {

    @Override
    public String code() {
        return PaymentProviderCodes.MANUAL;
    }

    @Override
    public CheckoutSession createCheckout(PaymentOrder order) {
        return new CheckoutSession("manual_" + order.paymentId(), null, PaymentStatus.PENDING_MANUAL);
    }

    @Override
    public PaymentStatusView verify(String providerReference) {
        if (providerReference == null || !providerReference.startsWith("manual_")) {
            throw BusinessException.notFound("error.payment.not_found");
        }
        return new PaymentStatusView(PaymentStatus.PENDING_MANUAL, providerReference);
    }

    @Override
    public ParsedWebhook handleWebhook(WebhookPayload payload) {
        throw BusinessException.badRequest("error.payment.manual_webhook_unsupported");
    }
}
