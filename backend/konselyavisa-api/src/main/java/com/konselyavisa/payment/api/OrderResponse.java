package com.konselyavisa.payment.api;

import com.konselyavisa.payment.domain.OrderStatus;
import java.util.UUID;

public record OrderResponse(
        UUID id,
        UUID caseId,
        String reference,
        String currency,
        long amountMinor,
        OrderStatus status,
        String providerCode,
        PaymentResponse payment) {}
