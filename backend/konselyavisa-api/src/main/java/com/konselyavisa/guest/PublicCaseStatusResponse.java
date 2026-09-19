package com.konselyavisa.guest;

import com.konselyavisa.dossier.CaseStatus;
import java.util.Map;

public record PublicCaseStatusResponse(
        String reference, CaseStatus status, Map<String, String> procedureNameI18n, String nextActionMessageKey) {}
