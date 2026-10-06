package com.konselyavisa.privacy.api;

import com.konselyavisa.privacy.domain.OrganizationDpaStatus;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record OrganizationDpaResponse(
        UUID id,
        String version,
        OrganizationDpaStatus status,
        int retentionDays,
        String processorLegalName,
        Map<String, String> controllerNameI18n,
        Map<String, String> summaryI18n,
        String documentUri,
        Instant acceptedAt,
        String acceptedByLabel) {}
