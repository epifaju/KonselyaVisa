package com.konselyavisa.document.api;

import com.konselyavisa.document.domain.DocumentStatus;
import com.konselyavisa.document.domain.ExtractionSource;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
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
        Map<String, String> extractedFields,
        Map<String, String> documentValidations,
        BigDecimal aiConfidence,
        ExtractionSource extractionSource,
        Instant createdAt) {

    public DocumentResponse withExtractedFields(Map<String, String> fields) {
        return new DocumentResponse(
                id,
                caseId,
                requirementCode,
                originalFilename,
                contentType,
                sizeBytes,
                sha256,
                status,
                duplicateHash,
                reviewMessageKey,
                fields,
                documentValidations,
                aiConfidence,
                extractionSource,
                createdAt);
    }
}
