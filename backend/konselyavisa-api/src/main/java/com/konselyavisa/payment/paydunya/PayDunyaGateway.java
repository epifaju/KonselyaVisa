package com.konselyavisa.payment.paydunya;

import com.konselyavisa.payment.provider.CheckoutSession;
import com.konselyavisa.payment.provider.ParsedWebhook;
import com.konselyavisa.payment.provider.PaymentOrder;
import com.konselyavisa.payment.provider.PaymentStatusView;
import com.konselyavisa.payment.provider.WebhookPayload;

public interface PayDunyaGateway {

    CheckoutSession createInvoice(PaymentOrder order);

    PaymentStatusView confirm(String invoiceToken);

    ParsedWebhook parseWebhook(WebhookPayload payload);
}
