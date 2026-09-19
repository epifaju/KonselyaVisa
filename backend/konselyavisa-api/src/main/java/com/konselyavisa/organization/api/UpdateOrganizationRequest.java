package com.konselyavisa.organization.api;

import com.konselyavisa.organization.domain.OrganizationStatus;
import java.util.Map;

public record UpdateOrganizationRequest(
        Map<String, String> nameI18n,
        String defaultLocale,
        String defaultCurrency,
        Map<String, Object> settings,
        OrganizationStatus status) {}
