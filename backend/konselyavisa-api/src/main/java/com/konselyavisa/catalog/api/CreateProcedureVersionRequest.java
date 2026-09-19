package com.konselyavisa.catalog.api;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public record CreateProcedureVersionRequest(
        UUID copyFromVersionId,
        Map<String, Object> eligibilityRules,
        List<Map<String, Object>> documentRequirements,
        Integer estimatedInstructionDays) {}
