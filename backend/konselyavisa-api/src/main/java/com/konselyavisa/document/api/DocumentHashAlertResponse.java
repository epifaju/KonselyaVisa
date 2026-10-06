package com.konselyavisa.document.api;

import java.util.List;
import java.util.UUID;

public record DocumentHashAlertResponse(
        UUID documentId, String requirementCode, List<DocumentHashMatchResponse> matches) {}
