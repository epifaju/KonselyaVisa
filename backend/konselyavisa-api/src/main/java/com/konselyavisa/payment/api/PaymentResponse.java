package com.konselyavisa.payment.api;

import com.konselyavisa.payment.domain.PaymentStatus;
import java.util.UUID;

public record PaymentResponse(
        UUID id,
        String providerCode,
        String providerReference,
        PaymentStatus status,
        String currency,
        long amountMinor,
        String checkoutUrl) {}
