package com.konselyavisa.payment.api;

import com.konselyavisa.common.api.ApiResponse;
import com.konselyavisa.payment.PaymentService;
import com.konselyavisa.payment.provider.WebhookPayload;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/payments")
public class StripeWebhookController {

    private final PaymentService paymentService;

    public StripeWebhookController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping(path = "/webhooks/STRIPE", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<Void>> stripeWebhook(
            @RequestHeader(value = "Stripe-Signature", required = false) String signature, HttpServletRequest request)
            throws IOException {
        String rawBody = new String(request.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        paymentService.handleWebhook("STRIPE", new WebhookPayload(Map.of(), signature, rawBody));
        return ResponseEntity.ok(ApiResponse.ok(null, "payment.webhook_accepted"));
    }

    @PostMapping("/{paymentId}/sync")
    @PreAuthorize("hasAnyRole('CITIZEN', 'COMPANY_USER', 'AGENT', 'SUPERVISOR', 'BUSINESS_ADMIN', 'PLATFORM_ADMIN')")
    public ApiResponse<OrderResponse> sync(@PathVariable UUID paymentId) {
        return ApiResponse.ok(paymentService.syncFromProvider(paymentId), "payment.synced");
    }
}
