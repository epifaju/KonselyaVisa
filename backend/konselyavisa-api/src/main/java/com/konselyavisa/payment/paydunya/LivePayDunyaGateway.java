package com.konselyavisa.payment.paydunya;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
public class LivePayDunyaGateway implements PayDunyaGateway {

    private final PayDunyaProperties properties;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public LivePayDunyaGateway(
            PayDunyaProperties properties, RestClient payDunyaRestClient, ObjectMapper objectMapper) {
        this.properties = properties;
        this.restClient = payDunyaRestClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public CheckoutSession createInvoice(PaymentOrder order) {
        requireConfigured();
        int amount = PayDunyaAmount.toMajorUnits(order.currency(), order.amountMinor());
        Map<String, Object> invoice = new LinkedHashMap<>();
        invoice.put("total_amount", amount);
        invoice.put("description", "KonselyaVisa " + order.orderReference());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("invoice", invoice);
        body.put("store", Map.of("name", properties.getStoreName()));
        body.put(
                "custom_data",
                Map.of(
                        "paymentId", order.paymentId().toString(),
                        "orderId", order.orderId().toString(),
                        "caseId", order.caseId().toString()));
        body.put(
                "actions",
                Map.of(
                        "callback_url", properties.getCallbackUrl(),
                        "return_url", properties.getReturnUrl(),
                        "cancel_url", properties.getCancelUrl()));
        try {
            JsonNode response = restClient
                    .post()
                    .uri("/v1/checkout-invoice/create")
                    .contentType(MediaType.APPLICATION_JSON)
                    .headers(this::authHeaders)
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);
            if (response == null || !"00".equals(text(response, "response_code"))) {
                throw BusinessException.badRequest("error.payment.paydunya_unavailable");
            }
            String token = text(response, "token");
            String url = text(response, "response_text");
            if (token.isBlank() || url.isBlank()) {
                throw BusinessException.badRequest("error.payment.paydunya_unavailable");
            }
            return new CheckoutSession(token, url, PaymentStatus.PENDING);
        } catch (RestClientException ex) {
            throw BusinessException.badRequest("error.payment.paydunya_unavailable");
        }
    }

    @Override
    public PaymentStatusView confirm(String invoiceToken) {
        requireConfigured();
        return new PaymentStatusView(fetchStatus(invoiceToken), invoiceToken);
    }

    @Override
    public ParsedWebhook parseWebhook(WebhookPayload payload) {
        if (!properties.isWebhookConfigured()) {
            throw BusinessException.badRequest("error.payment.paydunya_not_configured");
        }
        Map<String, Object> fields = payload.json() == null ? Map.of() : payload.json();
        Map<String, Object> data = PayDunyaIpnPayload.extract(fields, objectMapper);
        String hash = stringValue(data.get("hash"));
        if (!PayDunyaHash.matches(hash, properties.getMasterKey())) {
            throw BusinessException.badRequest("error.payment.webhook_signature");
        }
        String invoiceToken = invoiceToken(data);
        if (invoiceToken.isBlank()) {
            throw BusinessException.badRequest("error.payment.webhook_invalid");
        }
        // Always re-confirm with PayDunya before completing (do not trust IPN status alone).
        PaymentStatus status = fetchStatus(invoiceToken);
        String idempotencyKey = invoiceToken + ":" + status.name();
        if (status == PaymentStatus.PENDING) {
            return new ParsedWebhook(invoiceToken, idempotencyKey, false, true);
        }
        return new ParsedWebhook(invoiceToken, idempotencyKey, status == PaymentStatus.COMPLETED, false);
    }

    private PaymentStatus fetchStatus(String invoiceToken) {
        try {
            JsonNode response = restClient
                    .get()
                    .uri("/v1/checkout-invoice/confirm/{token}", invoiceToken)
                    .headers(this::authHeaders)
                    .retrieve()
                    .body(JsonNode.class);
            if (response == null || !"00".equals(text(response, "response_code"))) {
                throw BusinessException.badRequest("error.payment.paydunya_unavailable");
            }
            return mapStatus(text(response, "status"));
        } catch (RestClientException ex) {
            throw BusinessException.badRequest("error.payment.paydunya_unavailable");
        }
    }

    private void authHeaders(org.springframework.http.HttpHeaders headers) {
        headers.set("PAYDUNYA-MASTER-KEY", properties.getMasterKey());
        headers.set("PAYDUNYA-PRIVATE-KEY", properties.getPrivateKey());
        headers.set("PAYDUNYA-TOKEN", properties.getToken());
    }

    private void requireConfigured() {
        if (!properties.isConfigured()) {
            throw BusinessException.badRequest("error.payment.paydunya_not_configured");
        }
    }

    static PaymentStatus mapStatus(String remoteStatus) {
        if (remoteStatus == null || remoteStatus.isBlank()) {
            return PaymentStatus.PENDING;
        }
        return switch (remoteStatus.trim().toLowerCase(Locale.ROOT)) {
            case "completed" -> PaymentStatus.COMPLETED;
            case "cancelled", "canceled", "failed" -> PaymentStatus.FAILED;
            default -> PaymentStatus.PENDING;
        };
    }

    @SuppressWarnings("unchecked")
    private static String invoiceToken(Map<String, Object> data) {
        Object invoice = data.get("invoice");
        if (invoice instanceof Map<?, ?> map) {
            Object token = map.get("token");
            if (token != null && !String.valueOf(token).isBlank()) {
                return String.valueOf(token).trim();
            }
        }
        return stringValue(data.get("token"));
    }

    private static String text(JsonNode node, String field) {
        if (node == null || node.isMissingNode() || !node.has(field)) {
            return "";
        }
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? "" : value.asText();
    }

    private static String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
