package com.konselyavisa.payment.cinetpay;

import com.fasterxml.jackson.databind.JsonNode;
import com.konselyavisa.common.exception.BusinessException;
import com.konselyavisa.payment.domain.PaymentStatus;
import com.konselyavisa.payment.provider.CheckoutSession;
import com.konselyavisa.payment.provider.ParsedWebhook;
import com.konselyavisa.payment.provider.PaymentOrder;
import com.konselyavisa.payment.provider.PaymentStatusView;
import com.konselyavisa.payment.provider.WebhookPayload;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class LiveCinetPayGateway implements CinetPayGateway {

    private final CinetPayProperties properties;
    private final RestClient restClient;

    public LiveCinetPayGateway(CinetPayProperties properties, RestClient cinetPayRestClient) {
        this.properties = properties;
        this.restClient = cinetPayRestClient;
    }

    @Override
    public CheckoutSession createPayment(PaymentOrder order) {
        requireConfigured();
        String transactionId = order.paymentId().toString();
        int amount = CinetPayAmount.toMajorUnits(order.currency(), order.amountMinor());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("apikey", properties.getApiKey());
        body.put("site_id", properties.getSiteId());
        body.put("transaction_id", transactionId);
        body.put("amount", amount);
        body.put("currency", order.currency().toUpperCase(Locale.ROOT));
        body.put("description", "KonselyaVisa " + order.orderReference());
        body.put("notify_url", properties.getNotifyUrl());
        body.put("return_url", properties.getReturnUrl());
        body.put("channels", properties.getChannels());
        body.put("metadata", order.paymentId().toString());
        try {
            JsonNode response = restClient
                    .post()
                    .uri("/v2/payment")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);
            if (response == null || !"201".equals(text(response, "code"))) {
                throw BusinessException.badRequest("error.payment.cinetpay_unavailable");
            }
            String url = text(response.path("data"), "payment_url");
            if (url.isBlank()) {
                throw BusinessException.badRequest("error.payment.cinetpay_unavailable");
            }
            return new CheckoutSession(transactionId, url, PaymentStatus.PENDING);
        } catch (RestClientException ex) {
            throw BusinessException.badRequest("error.payment.cinetpay_unavailable");
        }
    }

    @Override
    public PaymentStatusView checkTransaction(String transactionId) {
        requireConfigured();
        return new PaymentStatusView(fetchStatus(transactionId), transactionId);
    }

    @Override
    public ParsedWebhook parseWebhook(WebhookPayload payload) {
        if (!properties.isWebhookConfigured()) {
            throw BusinessException.badRequest("error.payment.cinetpay_not_configured");
        }
        Map<String, Object> fields = payload.json() == null ? Map.of() : payload.json();
        String signature = payload.signature() != null ? payload.signature() : stringValue(fields.get("signature"));
        if (!CinetPayHmac.matches(fields, signature, properties.getSecretKey())) {
            throw BusinessException.badRequest("error.payment.webhook_signature");
        }
        String transactionId = firstNonBlank(stringValue(fields.get("cpm_trans_id")), stringValue(fields.get("transaction_id")));
        if (transactionId.isBlank()) {
            throw BusinessException.badRequest("error.payment.webhook_invalid");
        }
        PaymentStatus status = fetchStatus(transactionId);
        String idempotencyKey = transactionId + ":" + status.name();
        if (status == PaymentStatus.PENDING) {
            return new ParsedWebhook(transactionId, idempotencyKey, false, true);
        }
        return new ParsedWebhook(transactionId, idempotencyKey, status == PaymentStatus.COMPLETED, false);
    }

    private PaymentStatus fetchStatus(String transactionId) {
        Map<String, Object> body = Map.of(
                "apikey", properties.getApiKey(),
                "site_id", properties.getSiteId(),
                "transaction_id", transactionId);
        try {
            JsonNode response = restClient
                    .post()
                    .uri("/v2/payment/check")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);
            if (response == null) {
                throw BusinessException.badRequest("error.payment.cinetpay_unavailable");
            }
            String remoteStatus = text(response.path("data"), "status").toUpperCase(Locale.ROOT);
            return switch (remoteStatus) {
                case "ACCEPTED" -> PaymentStatus.COMPLETED;
                case "REFUSED", "CANCELED", "CANCELLED" -> PaymentStatus.FAILED;
                default -> PaymentStatus.PENDING;
            };
        } catch (RestClientException ex) {
            throw BusinessException.badRequest("error.payment.cinetpay_unavailable");
        }
    }

    private void requireConfigured() {
        if (!properties.isConfigured()) {
            throw BusinessException.badRequest("error.payment.cinetpay_not_configured");
        }
    }

    private static String text(JsonNode node, String field) {
        if (node == null || node.isMissingNode() || !node.has(field)) {
            return "";
        }
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? "" : value.asText();
    }

    private static String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static String firstNonBlank(String a, String b) {
        return a != null && !a.isBlank() ? a : (b == null ? "" : b);
    }
}
