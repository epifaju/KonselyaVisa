package com.konselyavisa.organization.service;

import com.konselyavisa.common.exception.BusinessException;
import com.konselyavisa.organization.OrganizationBrandingAssets;
import com.konselyavisa.organization.OrganizationBrandingSettings;
import com.konselyavisa.organization.api.OrganizationBrandingResponse;
import com.konselyavisa.organization.api.OrganizationResponse;
import com.konselyavisa.organization.domain.OrganizationStatus;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class OrganizationBrandingService {

    private final OrganizationService organizationService;

    public OrganizationBrandingService(OrganizationService organizationService) {
        this.organizationService = organizationService;
    }

    public OrganizationBrandingResponse current() {
        OrganizationResponse organization = organizationService.getCurrent();
        if (organization.status() != OrganizationStatus.ACTIVE) {
            throw BusinessException.notFound("error.organization.not_found");
        }
        Map<String, Object> settings = organization.settings() == null ? Map.of() : organization.settings();
        return new OrganizationBrandingResponse(
                organization.id(),
                organization.nameI18n(),
                organization.defaultLocale(),
                languages(settings, organization.defaultLocale()),
                OrganizationBrandingSettings.brandColor(settings),
                OrganizationBrandingSettings.domain(settings),
                OrganizationBrandingAssets.hasAsset(settings, OrganizationBrandingAssets.LOGO)
                        ? OrganizationBrandingAssets.publicPath(organization.id(), OrganizationBrandingAssets.LOGO)
                        : null,
                OrganizationBrandingAssets.hasAsset(settings, OrganizationBrandingAssets.FAVICON)
                        ? OrganizationBrandingAssets.publicPath(
                                organization.id(), OrganizationBrandingAssets.FAVICON)
                        : null);
    }

    static List<String> languages(Map<String, Object> settings, String defaultLocale) {
        Object raw = settings.get("activeLanguages");
        List<String> languages = new ArrayList<>();
        if (raw instanceof List<?> list) {
            for (Object item : list) {
                if (item != null) {
                    String code = item.toString().trim().toLowerCase();
                    if (!code.isBlank() && !languages.contains(code)) {
                        languages.add(code);
                    }
                }
            }
        }
        if (languages.isEmpty() && defaultLocale != null && !defaultLocale.isBlank()) {
            languages.add(defaultLocale.trim().toLowerCase());
        }
        if (languages.isEmpty()) {
            languages.add("fr");
        }
        return List.copyOf(languages);
    }
}
