package com.konselyavisa.payment.stripe;

import com.konselyavisa.payment.provider.CheckoutSession;
import com.konselyavisa.payment.provider.ParsedWebhook;
import com.konselyavisa.payment.provider.PaymentOrder;
import com.konselyavisa.payment.provider.PaymentStatusView;
import com.konselyavisa.payment.provider.WebhookPayload;

public interface StripeGateway {

    CheckoutSession createCheckoutSession(PaymentOrder order);

    PaymentStatusView retrieveSession(String providerReference);

    ParsedWebhook parseWebhook(WebhookPayload payload);
}
