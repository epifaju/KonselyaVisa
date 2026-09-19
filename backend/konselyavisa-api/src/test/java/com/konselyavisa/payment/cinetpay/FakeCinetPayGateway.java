package com.konselyavisa.payment.cinetpay;

import com.konselyavisa.common.exception.BusinessException;
import com.konselyavisa.payment.domain.PaymentStatus;
import com.konselyavisa.payment.provider.CheckoutSession;
import com.konselyavisa.payment.provider.ParsedWebhook;
import com.konselyavisa.payment.provider.PaymentOrder;
import com.konselyavisa.payment.provider.PaymentStatusView;
import com.konselyavisa.payment.provider.WebhookPayload;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class FakeCinetPayGateway implements CinetPayGateway {

    public static final String SECRET = "cinetpay-test-secret";

    private final Map<String, PaymentStatus> transactions = new ConcurrentHashMap<>();

    @Override
    public CheckoutSession createPayment(PaymentOrder order) {
        String transactionId = order.paymentId().toString();
        CinetPayAmount.toMajorUnits(order.currency(), order.amountMinor());
        transactions.put(transactionId, PaymentStatus.PENDING);
        return new CheckoutSession(
                transactionId, "https://checkout.cinetpay.com/payment/" + transactionId, PaymentStatus.PENDING);
    }

    @Override
    public PaymentStatusView checkTransaction(String transactionId) {
        return new PaymentStatusView(transactions.getOrDefault(transactionId, PaymentStatus.PENDING), transactionId);
    }

    public void markAccepted(String transactionId) {
        transactions.put(transactionId, PaymentStatus.COMPLETED);
    }

    @Override
    public ParsedWebhook parseWebhook(WebhookPayload payload) {
        Map<String, Object> fields = payload.json() == null ? Map.of() : payload.json();
        String signature = payload.signature() != null ? payload.signature() : String.valueOf(fields.get("signature"));
        if (!CinetPayHmac.matches(fields, signature, SECRET)) {
            throw BusinessException.badRequest("error.payment.webhook_signature");
        }
        Object trans = fields.get("cpm_trans_id");
        if (!(trans instanceof String transactionId) || transactionId.isBlank()) {
            throw BusinessException.badRequest("error.payment.webhook_invalid");
        }
        PaymentStatus status = transactions.getOrDefault(transactionId, PaymentStatus.PENDING);
        if ("ACCEPTED".equals(fields.get("cpm_result")) || "SUCCES".equals(fields.get("cpm_error_message"))) {
            status = PaymentStatus.COMPLETED;
            transactions.put(transactionId, status);
        } else if ("REFUSED".equals(fields.get("cpm_result"))) {
            status = PaymentStatus.FAILED;
            transactions.put(transactionId, status);
        }
        String idempotencyKey = transactionId + ":" + status.name();
        if (status == PaymentStatus.PENDING) {
            return new ParsedWebhook(transactionId, idempotencyKey, false, true);
        }
        return new ParsedWebhook(transactionId, idempotencyKey, status == PaymentStatus.COMPLETED, false);
    }
}
