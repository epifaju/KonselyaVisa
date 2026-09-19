package com.konselyavisa.catalog.api;

import java.util.List;
import java.util.Map;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record UpdateProcedureVersionRequest(
        Map<String, Object> eligibilityRules,
        List<Map<String, Object>> documentRequirements,
        @Min(1) @Max(365) Integer estimatedInstructionDays) {}
