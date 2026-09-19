package com.konselyavisa.dossier.api;

import com.konselyavisa.dossier.CaseStatus;
import java.util.Map;

public record CompanyCaseSummaryResponse(long total, Map<CaseStatus, Long> byStatus) {}
