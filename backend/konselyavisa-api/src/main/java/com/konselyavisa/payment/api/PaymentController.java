package com.konselyavisa.payment.api;

import com.konselyavisa.common.api.ApiResponse;
import com.konselyavisa.common.exception.BusinessException;
import com.konselyavisa.payment.PaymentService;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/payments")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping("/webhooks/{providerCode}")
    public ResponseEntity<ApiResponse<Void>> webhook(
            @PathVariable String providerCode, @RequestBody Map<String, Object> payload) {
        if ("STRIPE".equalsIgnoreCase(providerCode) || "CINETPAY".equalsIgnoreCase(providerCode)) {
            throw BusinessException.badRequest("error.payment.webhook_invalid");
        }
        paymentService.handleWebhook(providerCode, payload);
        return ResponseEntity.ok(ApiResponse.ok(null, "payment.webhook_accepted"));
    }

    @PostMapping("/{paymentId}/confirm-manual")
    @PreAuthorize("hasAnyRole('AGENT', 'SUPERVISOR', 'BUSINESS_ADMIN', 'PLATFORM_ADMIN')")
    public ApiResponse<OrderResponse> confirmManual(@PathVariable UUID paymentId) {
        return ApiResponse.ok(paymentService.confirmManual(paymentId), "payment.completed");
    }
}
