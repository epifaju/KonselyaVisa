package com.konselyavisa.document.api;

import com.konselyavisa.document.domain.DocumentStatus;
import java.time.Instant;
import java.util.UUID;

public record DocumentResponse(
        UUID id,
        UUID caseId,
        String requirementCode,
        String originalFilename,
        String contentType,
        long sizeBytes,
        String sha256,
        DocumentStatus status,
        boolean duplicateHash,
        String reviewMessageKey,
        Instant createdAt) {}
