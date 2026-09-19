package com.konselyavisa.outbox;

public interface OutboxWebhookClient {

    void send(OutboxWebhookMessage message);
}
