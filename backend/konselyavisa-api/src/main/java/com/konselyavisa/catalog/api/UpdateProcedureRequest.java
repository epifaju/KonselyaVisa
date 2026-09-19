package com.konselyavisa.catalog.api;

import com.konselyavisa.catalog.domain.ProcedureCategory;
import java.util.Map;
import java.util.UUID;

public record UpdateProcedureRequest(
        UUID originCountryId,
        UUID destinationCountryId,
        ProcedureCategory category,
        Map<String, String> nameI18n,
        Map<String, String> descriptionI18n,
        Boolean active) {}
