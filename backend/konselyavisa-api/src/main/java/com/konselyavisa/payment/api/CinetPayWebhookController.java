package com.konselyavisa.payment.api;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.konselyavisa.common.api.ApiResponse;
import com.konselyavisa.common.exception.BusinessException;
import com.konselyavisa.payment.PaymentService;
import com.konselyavisa.payment.provider.PaymentProviderCodes;
import com.konselyavisa.payment.provider.WebhookPayload;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/payments")
public class CinetPayWebhookController {

    private final PaymentService paymentService;
    private final ObjectMapper objectMapper;

    public CinetPayWebhookController(PaymentService paymentService, ObjectMapper objectMapper) {
        this.paymentService = paymentService;
        this.objectMapper = objectMapper;
    }

    @PostMapping(path = "/webhooks/CINETPAY")
    public ResponseEntity<ApiResponse<Void>> cinetPayWebhook(HttpServletRequest request) throws IOException {
        String rawBody = new String(request.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        Map<String, Object> fields = parseFields(request.getContentType(), rawBody);
        String signature = fields.get("signature") == null ? null : String.valueOf(fields.get("signature"));
        paymentService.handleWebhook(PaymentProviderCodes.CINETPAY, new WebhookPayload(fields, signature, rawBody));
        return ResponseEntity.ok(ApiResponse.ok(null, "payment.webhook_accepted"));
    }

    private Map<String, Object> parseFields(String contentType, String rawBody) {
        if (contentType != null && contentType.toLowerCase(Locale.ROOT).contains("application/x-www-form-urlencoded")) {
            return parseForm(rawBody);
        }
        try {
            if (rawBody == null || rawBody.isBlank()) {
                return Map.of();
            }
            return objectMapper.readValue(rawBody, new TypeReference<>() {});
        } catch (Exception ex) {
            throw BusinessException.badRequest("error.payment.webhook_invalid");
        }
    }

    static Map<String, Object> parseForm(String rawBody) {
        Map<String, Object> fields = new LinkedHashMap<>();
        if (rawBody == null || rawBody.isBlank()) {
            return fields;
        }
        for (String pair : rawBody.split("&")) {
            int eq = pair.indexOf('=');
            String key = eq < 0 ? pair : pair.substring(0, eq);
            String value = eq < 0 ? "" : pair.substring(eq + 1);
            fields.put(urlDecode(key), urlDecode(value));
        }
        return fields;
    }

    private static String urlDecode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }
}
