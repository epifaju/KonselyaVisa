package com.konselyavisa.organization.api;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public record OrganizationBrandingResponse(
        UUID organizationId,
        Map<String, String> nameI18n,
        String defaultLocale,
        List<String> activeLanguages,
        String brandColor,
        String domain,
        String logoUrl,
        String faviconUrl) {}
