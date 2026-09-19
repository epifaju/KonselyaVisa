package com.konselyavisa.catalog.api;

import com.konselyavisa.catalog.domain.ProcedureCategory;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record ProcedureResponse(
        UUID id,
        String code,
        CountryResponse originCountry,
        CountryResponse destinationCountry,
        ProcedureCategory category,
        Map<String, String> nameI18n,
        Map<String, String> descriptionI18n,
        boolean active,
        Integer publishedVersionNumber,
        List<ProcedureVersionResponse> versions) {}
