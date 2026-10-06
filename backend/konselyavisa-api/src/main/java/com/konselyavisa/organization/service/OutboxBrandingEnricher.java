package com.konselyavisa.organization.service;

import com.konselyavisa.organization.KonselyaPublicProperties;
import com.konselyavisa.organization.OrganizationBrandingAssets;
import com.konselyavisa.organization.OrganizationBrandingSettings;
import com.konselyavisa.organization.api.OrganizationResponse;
import com.konselyavisa.organization.domain.OrganizationStatus;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** Builds a non-PII branding snippet for outbox → n8n branded emails. */
@Service
public class OutboxBrandingEnricher {

    private final OrganizationService organizationService;
    private final KonselyaPublicProperties publicProperties;

    public OutboxBrandingEnricher(
            OrganizationService organizationService, KonselyaPublicProperties publicProperties) {
        this.organizationService = organizationService;
        this.publicProperties = publicProperties;
    }

    public Map<String, Object> brandingFor(UUID organizationId) {
        if (organizationId == null) {
            return Map.of();
        }
        try {
            OrganizationResponse organization = organizationService.getById(organizationId);
            if (organization.status() != OrganizationStatus.ACTIVE) {
                return Map.of();
            }
            Map<String, Object> settings = organization.settings() == null ? Map.of() : organization.settings();
            Map<String, Object> branding = new LinkedHashMap<>();
            branding.put("organizationId", organizationId.toString());
            branding.put("nameI18n", organization.nameI18n());
            String color = OrganizationBrandingSettings.brandColor(settings);
            if (color != null) {
                branding.put("brandColor", color);
            }
            String domain = OrganizationBrandingSettings.domain(settings);
            if (domain != null) {
                branding.put("domain", domain);
            }
            if (OrganizationBrandingAssets.hasAsset(settings, OrganizationBrandingAssets.LOGO)) {
                branding.put(
                        "logoUrl",
                        publicProperties.absoluteUrl(
                                OrganizationBrandingAssets.publicPath(organizationId, OrganizationBrandingAssets.LOGO)));
            }
            return branding;
        } catch (RuntimeException ignored) {
            return Map.of();
        }
    }
}
