package com.konselyavisa.payment.provider;

import com.konselyavisa.payment.cinetpay.CinetPayGateway;
import org.springframework.stereotype.Component;

@Component
public class CinetPayPaymentProvider implements PaymentProvider {

    private final CinetPayGateway cinetPayGateway;

    public CinetPayPaymentProvider(CinetPayGateway cinetPayGateway) {
        this.cinetPayGateway = cinetPayGateway;
    }

    @Override
    public String code() {
        return PaymentProviderCodes.CINETPAY;
    }

    @Override
    public CheckoutSession createCheckout(PaymentOrder order) {
        return cinetPayGateway.createPayment(order);
    }

    @Override
    public PaymentStatusView verify(String providerReference) {
        return cinetPayGateway.checkTransaction(providerReference);
    }

    @Override
    public ParsedWebhook handleWebhook(WebhookPayload payload) {
        return cinetPayGateway.parseWebhook(payload);
    }
}
