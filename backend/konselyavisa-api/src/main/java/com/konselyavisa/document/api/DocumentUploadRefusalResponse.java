package com.konselyavisa.document.api;

import java.time.Instant;
import java.util.UUID;

public record DocumentUploadRefusalResponse(
        UUID id,
        UUID caseId,
        String requirementCode,
        String reasonKey,
        String contentType,
        Long sizeBytes,
        String originalFilename,
        Instant attemptedAt,
        String attemptedBy) {}
