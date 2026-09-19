package com.konselyavisa.payment.api;

import com.konselyavisa.common.api.ApiResponse;
import com.konselyavisa.payment.PaymentService;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/cases/{caseId}/orders")
public class CaseOrderController {

    private final PaymentService paymentService;

    public CaseOrderController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('CITIZEN', 'COMPANY_USER', 'AGENT', 'SUPERVISOR', 'BUSINESS_ADMIN', 'PLATFORM_ADMIN')")
    public ApiResponse<OrderResponse> create(@PathVariable UUID caseId) {
        return ApiResponse.ok(paymentService.createCheckout(caseId), "order.created");
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('CITIZEN', 'COMPANY_USER', 'AGENT', 'SUPERVISOR', 'BUSINESS_ADMIN', 'PLATFORM_ADMIN')")
    public ApiResponse<List<OrderResponse>> list(@PathVariable UUID caseId) {
        return ApiResponse.ok(paymentService.listByCase(caseId));
    }
}
