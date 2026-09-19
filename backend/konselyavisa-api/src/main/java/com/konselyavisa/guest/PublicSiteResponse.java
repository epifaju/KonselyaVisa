package com.konselyavisa.guest;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public record PublicSiteResponse(
        UUID organizationId,
        Map<String, String> nameI18n,
        String defaultLocale,
        List<String> activeLanguages,
        Map<String, String> addressI18n,
        Map<String, String> openingHoursI18n,
        String contactEmail,
        String contactPhone,
        List<PublicFormalityResponse> formalities) {}
