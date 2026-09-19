package com.konselyavisa.payment.provider;

import com.konselyavisa.payment.domain.PaymentStatus;

public record CheckoutSession(String providerReference, String checkoutUrl, PaymentStatus paymentStatus) {}
