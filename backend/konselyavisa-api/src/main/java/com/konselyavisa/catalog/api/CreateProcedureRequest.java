package com.konselyavisa.catalog.api;

import com.konselyavisa.catalog.domain.ProcedureCategory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record CreateProcedureRequest(
        @NotBlank @Size(max = 80) String code,
        @NotNull UUID originCountryId,
        @NotNull UUID destinationCountryId,
        @NotNull ProcedureCategory category,
        @NotEmpty Map<String, String> nameI18n,
        Map<String, String> descriptionI18n,
        Map<String, Object> eligibilityRules,
        List<Map<String, Object>> documentRequirements) {}
