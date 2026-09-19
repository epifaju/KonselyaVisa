package com.konselyavisa.dossier.api;

import com.konselyavisa.catalog.api.CountryResponse;
import com.konselyavisa.dossier.CaseListUrgency;
import com.konselyavisa.dossier.CaseListUrgencyGroup;
import com.konselyavisa.dossier.CaseNextAction;
import com.konselyavisa.dossier.CaseNextActionDecision;
import com.konselyavisa.dossier.CaseStatus;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record CaseResponse(
        UUID id,
        String reference,
        CaseStatus status,
        UUID organizationId,
        ApplicantResponse applicant,
        UUID procedureDefinitionId,
        String procedureCode,
        Map<String, String> procedureNameI18n,
        CountryResponse originCountry,
        CountryResponse destinationCountry,
        UUID procedureVersionId,
        int procedureVersionNumber,
        Integer estimatedInstructionDays,
        Map<String, Object> applicantFacts,
        boolean eligibilityPassed,
        Instant createdAt,
        Instant updatedAt,
        String createdBy,
        String createdByRole,
        String createdByLabel,
        CaseNextAction nextAction,
        String nextActionMessageKey,
        String correctionMessageKey,
        CaseListUrgencyGroup listUrgencyGroup,
        String listActionMessageKey,
        String listSubtitleMessageKey) {

    public CaseResponse withProgress(CaseNextActionDecision decision) {
        CaseNextAction action = decision.nextAction();
        return new CaseResponse(
                id,
                reference,
                status,
                organizationId,
                applicant,
                procedureDefinitionId,
                procedureCode,
                procedureNameI18n,
                originCountry,
                destinationCountry,
                procedureVersionId,
                procedureVersionNumber,
                estimatedInstructionDays,
                applicantFacts,
                eligibilityPassed,
                createdAt,
                updatedAt,
                createdBy,
                createdByRole,
                createdByLabel,
                action,
                decision.nextActionMessageKey(),
                decision.correctionMessageKey(),
                CaseListUrgency.groupOf(status, action),
                CaseListUrgency.actionMessageKey(action),
                CaseListUrgency.subtitleMessageKey(action));
    }
}
