package com.konselyavisa.payment.provider;

import java.util.Map;

public record WebhookPayload(Map<String, Object> json, String signature, String rawBody) {

    public WebhookPayload(Map<String, Object> json, String signature) {
        this(json, signature, null);
    }
}
