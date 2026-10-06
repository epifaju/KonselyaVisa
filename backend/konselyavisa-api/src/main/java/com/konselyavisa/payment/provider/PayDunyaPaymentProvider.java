package com.konselyavisa.payment.provider;

import com.konselyavisa.payment.paydunya.PayDunyaGateway;
import org.springframework.stereotype.Component;

@Component
public class PayDunyaPaymentProvider implements PaymentProvider {

    private final PayDunyaGateway payDunyaGateway;

    public PayDunyaPaymentProvider(PayDunyaGateway payDunyaGateway) {
        this.payDunyaGateway = payDunyaGateway;
    }

    @Override
    public String code() {
        return PaymentProviderCodes.PAYDUNYA;
    }

    @Override
    public CheckoutSession createCheckout(PaymentOrder order) {
        return payDunyaGateway.createInvoice(order);
    }

    @Override
    public PaymentStatusView verify(String providerReference) {
        return payDunyaGateway.confirm(providerReference);
    }

    @Override
    public ParsedWebhook handleWebhook(WebhookPayload payload) {
        return payDunyaGateway.parseWebhook(payload);
    }
}
