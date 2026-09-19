package com.konselyavisa.payment.provider;

import java.util.UUID;

public record PaymentOrder(
        UUID organizationId,
        UUID caseId,
        UUID orderId,
        UUID paymentId,
        String orderReference,
        String currency,
        long amountMinor) {}
