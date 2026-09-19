package com.konselyavisa.payment.provider;

public interface PaymentProvider {

    String code();

    CheckoutSession createCheckout(PaymentOrder order);

    PaymentStatusView verify(String providerReference);

    ParsedWebhook handleWebhook(WebhookPayload payload);
}
