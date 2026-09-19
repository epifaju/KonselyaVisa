package com.konselyavisa.dossier.api;

import java.time.Instant;
import java.util.UUID;

public record CaseHistoryEventResponse(
        UUID id,
        String eventType,
        Instant occurredAt,
        String actorKind,
        String messageKey,
        String requirementCode) {}
