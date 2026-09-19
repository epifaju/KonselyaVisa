package com.konselyavisa.payment.provider;

public record ParsedWebhook(String providerReference, String idempotencyKey, boolean completed, boolean ignored) {

    public ParsedWebhook(String providerReference, String idempotencyKey, boolean completed) {
        this(providerReference, idempotencyKey, completed, false);
    }
}
