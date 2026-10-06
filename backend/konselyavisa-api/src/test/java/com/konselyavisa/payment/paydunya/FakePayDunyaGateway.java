package com.konselyavisa.payment.paydunya;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.konselyavisa.common.exception.BusinessException;
import com.konselyavisa.payment.domain.PaymentStatus;
import com.konselyavisa.payment.provider.CheckoutSession;
import com.konselyavisa.payment.provider.ParsedWebhook;
import com.konselyavisa.payment.provider.PaymentOrder;
import com.konselyavisa.payment.provider.PaymentStatusView;
import com.konselyavisa.payment.provider.WebhookPayload;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class FakePayDunyaGateway implements PayDunyaGateway {

    public static final String MASTER_KEY = "paydunya-test-master-key";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<String, PaymentStatus> invoices = new ConcurrentHashMap<>();

    @Override
    public CheckoutSession createInvoice(PaymentOrder order) {
        PayDunyaAmount.toMajorUnits(order.currency(), order.amountMinor());
        String token = "test_" + order.paymentId().toString().replace("-", "").substring(0, 12);
        invoices.put(token, PaymentStatus.PENDING);
        return new CheckoutSession(
                token, "https://app.paydunya.com/sandbox-checkout/invoice/" + token, PaymentStatus.PENDING);
    }

    @Override
    public PaymentStatusView confirm(String invoiceToken) {
        return new PaymentStatusView(invoices.getOrDefault(invoiceToken, PaymentStatus.PENDING), invoiceToken);
    }

    public void markCompleted(String invoiceToken) {
        invoices.put(invoiceToken, PaymentStatus.COMPLETED);
    }

    @Override
    public ParsedWebhook parseWebhook(WebhookPayload payload) {
        Map<String, Object> fields = payload.json() == null ? Map.of() : payload.json();
        Map<String, Object> data = PayDunyaIpnPayload.extract(fields, objectMapper);
        String hash = data.get("hash") == null ? "" : String.valueOf(data.get("hash"));
        if (!PayDunyaHash.matches(hash, MASTER_KEY)) {
            throw BusinessException.badRequest("error.payment.webhook_signature");
        }
        Object invoice = data.get("invoice");
        String token = "";
        if (invoice instanceof Map<?, ?> map && map.get("token") != null) {
            token = String.valueOf(map.get("token"));
        }
        if (token.isBlank()) {
            throw BusinessException.badRequest("error.payment.webhook_invalid");
        }
        PaymentStatus status = invoices.getOrDefault(token, PaymentStatus.PENDING);
        Object remote = data.get("status");
        if ("completed".equalsIgnoreCase(String.valueOf(remote))) {
            status = PaymentStatus.COMPLETED;
            invoices.put(token, status);
        } else if ("failed".equalsIgnoreCase(String.valueOf(remote))
                || "cancelled".equalsIgnoreCase(String.valueOf(remote))
                || "canceled".equalsIgnoreCase(String.valueOf(remote))) {
            status = PaymentStatus.FAILED;
            invoices.put(token, status);
        }
        String idempotencyKey = token + ":" + status.name();
        if (status == PaymentStatus.PENDING) {
            return new ParsedWebhook(token, idempotencyKey, false, true);
        }
        return new ParsedWebhook(token, idempotencyKey, status == PaymentStatus.COMPLETED, false);
    }

    public static Map<String, Object> completedIpn(String invoiceToken) {
        return Map.of(
                "data",
                Map.of(
                        "hash",
                        PayDunyaHash.ofMasterKey(MASTER_KEY),
                        "status",
                        "completed",
                        "invoice",
                        Map.of("token", invoiceToken),
                        "custom_data",
                        Map.of("paymentId", UUID.randomUUID().toString())));
    }
}
