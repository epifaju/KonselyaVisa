package com.konselyavisa.payment.provider;

import com.konselyavisa.payment.stripe.StripeGateway;
import org.springframework.stereotype.Component;

@Component
public class StripePaymentProvider implements PaymentProvider {

    private final StripeGateway stripeGateway;

    public StripePaymentProvider(StripeGateway stripeGateway) {
        this.stripeGateway = stripeGateway;
    }

    @Override
    public String code() {
        return PaymentProviderCodes.STRIPE;
    }

    @Override
    public CheckoutSession createCheckout(PaymentOrder order) {
        return stripeGateway.createCheckoutSession(order);
    }

    @Override
    public PaymentStatusView verify(String providerReference) {
        return stripeGateway.retrieveSession(providerReference);
    }

    @Override
    public ParsedWebhook handleWebhook(WebhookPayload payload) {
        return stripeGateway.parseWebhook(payload);
    }
}
