package com.konselyavisa.organization.api;

import com.konselyavisa.organization.domain.OrganizationStatus;
import java.util.Map;
import java.util.UUID;

public record OrganizationResponse(
        UUID id,
        String code,
        String slug,
        Map<String, String> nameI18n,
        OrganizationStatus status,
        String defaultLocale,
        String defaultCurrency,
        Map<String, Object> settings) {}
