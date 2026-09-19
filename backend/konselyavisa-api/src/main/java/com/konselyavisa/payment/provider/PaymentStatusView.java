package com.konselyavisa.payment.provider;

import com.konselyavisa.payment.domain.PaymentStatus;

public record PaymentStatusView(PaymentStatus status, String providerReference) {}
