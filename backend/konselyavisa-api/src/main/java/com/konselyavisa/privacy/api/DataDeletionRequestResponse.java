package com.konselyavisa.privacy.api;

import com.konselyavisa.privacy.domain.DataDeletionStatus;
import java.time.Instant;
import java.util.UUID;

public record DataDeletionRequestResponse(
        UUID id, DataDeletionStatus status, Instant requestedAt, Instant scheduledAnonymizeAt, Instant completedAt) {}
