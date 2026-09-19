package com.konselyavisa.privacy.api;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record PersonalDataExportResponse(
        Instant exportedAt,
        String keycloakSubject,
        ApplicantExport applicant,
        List<ConsentExport> consents,
        List<CaseExport> cases,
        List<DeletionExport> deletionRequests) {

    public record ApplicantExport(String email, String displayName, Map<String, Object> facts) {}

    public record ConsentExport(String purpose, String noticeVersion, String locale, Instant acceptedAt) {}

    public record CaseExport(
            String reference,
            String status,
            String procedureCode,
            Instant createdAt,
            Instant updatedAt,
            List<DocumentExport> documents) {}

    public record DocumentExport(String requirementCode, String status, Instant createdAt) {}

    public record DeletionExport(String status, Instant requestedAt, Instant scheduledAnonymizeAt) {}
}
