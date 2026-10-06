package com.konselyavisa.document.api;

import java.util.UUID;

public record DocumentHashMatchResponse(
        UUID caseId, String caseReference, String applicantDisplayName, String requirementCode) {}
