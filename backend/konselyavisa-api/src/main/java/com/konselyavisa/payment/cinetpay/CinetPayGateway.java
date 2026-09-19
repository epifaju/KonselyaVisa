package com.konselyavisa.payment.cinetpay;

import com.konselyavisa.payment.provider.CheckoutSession;
import com.konselyavisa.payment.provider.ParsedWebhook;
import com.konselyavisa.payment.provider.PaymentOrder;
import com.konselyavisa.payment.provider.PaymentStatusView;
import com.konselyavisa.payment.provider.WebhookPayload;

public interface CinetPayGateway {

    CheckoutSession createPayment(PaymentOrder order);

    PaymentStatusView checkTransaction(String transactionId);

    ParsedWebhook parseWebhook(WebhookPayload payload);
}
