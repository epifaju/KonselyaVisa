package com.konselyavisa.outbox;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record OutboxWebhookMessage(
        UUID id,
        UUID organizationId,
        String eventType,
        String aggregateType,
        UUID aggregateId,
        Instant occurredAt,
        Map<String, Object> payload) {}
