package com.konselyavisa.dossier.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record CreateCaseRequest(
        @NotNull UUID procedureDefinitionId,
        @Valid ApplicantRequest applicant,
        @Valid @NotNull PrivacyConsentAcceptance privacyConsent,
        UUID eligibilityTicketId) {

    public CreateCaseRequest(UUID procedureDefinitionId, ApplicantRequest applicant) {
        this(procedureDefinitionId, applicant, PrivacyConsentAcceptance.currentAccepted("fr"), null);
    }

    public CreateCaseRequest(
            UUID procedureDefinitionId, ApplicantRequest applicant, PrivacyConsentAcceptance privacyConsent) {
        this(procedureDefinitionId, applicant, privacyConsent, null);
    }
}
