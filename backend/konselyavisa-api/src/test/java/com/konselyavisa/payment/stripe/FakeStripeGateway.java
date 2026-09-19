package com.konselyavisa.payment.stripe;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.konselyavisa.common.exception.BusinessException;
import com.konselyavisa.payment.domain.PaymentStatus;
import com.konselyavisa.payment.provider.CheckoutSession;
import com.konselyavisa.payment.provider.ParsedWebhook;
import com.konselyavisa.payment.provider.PaymentOrder;
import com.konselyavisa.payment.provider.PaymentStatusView;
import com.konselyavisa.payment.provider.WebhookPayload;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class FakeStripeGateway implements StripeGateway {

    public static final String VALID_SIGNATURE = "t=1,v1=test";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<String, PaymentStatus> sessions = new ConcurrentHashMap<>();

    @Override
    public CheckoutSession createCheckoutSession(PaymentOrder order) {
        String reference = "cs_test_" + order.paymentId();
        sessions.put(reference, PaymentStatus.PENDING);
        return new CheckoutSession(reference, "https://checkout.stripe.com/c/pay/" + reference, PaymentStatus.PENDING);
    }

    @Override
    public PaymentStatusView retrieveSession(String providerReference) {
        PaymentStatus status = sessions.getOrDefault(providerReference, PaymentStatus.PENDING);
        return new PaymentStatusView(status, providerReference);
    }

    public void markPaid(String providerReference) {
        sessions.put(providerReference, PaymentStatus.COMPLETED);
    }

    @Override
    public ParsedWebhook parseWebhook(WebhookPayload payload) {
        if (!VALID_SIGNATURE.equals(payload.signature())) {
            throw BusinessException.badRequest("error.payment.webhook_signature");
        }
        try {
            JsonNode root = objectMapper.readTree(payload.rawBody());
            String type = root.path("type").asText();
            String eventId = root.path("id").asText();
            String sessionId = root.path("data").path("object").path("id").asText();
            if (type.startsWith("checkout.session.")) {
                boolean completed = "checkout.session.completed".equals(type)
                        || "checkout.session.async_payment_succeeded".equals(type);
                if (completed) {
                    sessions.put(sessionId, PaymentStatus.COMPLETED);
                }
                return new ParsedWebhook(sessionId, eventId, completed, false);
            }
            return new ParsedWebhook(null, eventId, false, true);
        } catch (Exception ex) {
            throw BusinessException.badRequest("error.payment.webhook_invalid");
        }
    }
}
