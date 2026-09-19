package com.konselyavisa.catalog.api;

import com.konselyavisa.catalog.domain.ProcedureVersionStatus;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record ProcedureVersionResponse(
        UUID id,
        int versionNumber,
        ProcedureVersionStatus status,
        Map<String, Object> eligibilityRules,
        List<Map<String, Object>> documentRequirements,
        Map<String, Object> pricing,
        Integer estimatedInstructionDays,
        Instant publishedAt) {}
