package com.konselyavisa.organization.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.konselyavisa.common.exception.BusinessException;
import com.konselyavisa.organization.KonselyaPublicProperties;
import com.konselyavisa.organization.OrganizationBrandingAssets;
import com.konselyavisa.organization.api.OrganizationResponse;
import com.konselyavisa.organization.domain.OrganizationStatus;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OutboxBrandingEnricherTest {

    @Mock
    private OrganizationService organizationService;

    private KonselyaPublicProperties publicProperties;
    private OutboxBrandingEnricher enricher;

    @BeforeEach
    void setUp() {
        publicProperties = new KonselyaPublicProperties();
        publicProperties.setBaseUrl("https://api.example.com/");
        enricher = new OutboxBrandingEnricher(organizationService, publicProperties);
    }

    @Test
    void brandingForReturnsEmptyWhenOrganizationIdNull() {
        assertThat(enricher.brandingFor(null)).isEmpty();
    }

    @Test
    void brandingForReturnsEmptyWhenOrganizationInactive() {
        UUID orgId = UUID.randomUUID();
        when(organizationService.getById(orgId))
                .thenReturn(org(orgId, OrganizationStatus.SUSPENDED, Map.of("fr", "Demo"), Map.of()));

        assertThat(enricher.brandingFor(orgId)).isEmpty();
    }

    @Test
    void brandingForIncludesNameAndOptionalBrandFields() {
        UUID orgId = UUID.randomUUID();
        when(organizationService.getById(orgId))
                .thenReturn(org(
                        orgId,
                        OrganizationStatus.ACTIVE,
                        Map.of("fr", "Demo"),
                        Map.of("brandColor", "#1a4d8c", "domain", "visa.example")));

        Map<String, Object> branding = enricher.brandingFor(orgId);

        assertThat(branding)
                .containsEntry("organizationId", orgId.toString())
                .containsEntry("nameI18n", Map.of("fr", "Demo"))
                .containsEntry("brandColor", "#1A4D8C")
                .containsEntry("domain", "visa.example")
                .doesNotContainKey("logoUrl");
    }

    @Test
    void brandingForIncludesLogoUrlWhenAssetConfigured() {
        UUID orgId = UUID.randomUUID();
        when(organizationService.getById(orgId))
                .thenReturn(org(
                        orgId,
                        OrganizationStatus.ACTIVE,
                        Map.of("fr", "Demo"),
                        Map.of(OrganizationBrandingAssets.contentTypeKey(OrganizationBrandingAssets.LOGO), "image/png")));

        Map<String, Object> branding = enricher.brandingFor(orgId);

        assertThat(branding)
                .containsEntry(
                        "logoUrl",
                        "https://api.example.com"
                                + OrganizationBrandingAssets.publicPath(orgId, OrganizationBrandingAssets.LOGO));
    }

    @Test
    void brandingForReturnsEmptyWhenOrganizationServiceThrows() {
        UUID orgId = UUID.randomUUID();
        when(organizationService.getById(orgId)).thenThrow(BusinessException.notFound("error.organization.not_found"));

        assertThat(enricher.brandingFor(orgId)).isEmpty();
    }

    @Test
    void brandingForOmitsNullOptionalFields() {
        UUID orgId = UUID.randomUUID();
        when(organizationService.getById(orgId))
                .thenReturn(org(orgId, OrganizationStatus.ACTIVE, Map.of("en", "Demo"), null));

        Map<String, Object> branding = enricher.brandingFor(orgId);

        assertThat(branding)
                .containsOnlyKeys("organizationId", "nameI18n")
                .containsEntry("nameI18n", Map.of("en", "Demo"));
    }

    private static OrganizationResponse org(
            UUID id, OrganizationStatus status, Map<String, String> nameI18n, Map<String, Object> settings) {
        return new OrganizationResponse(id, "DEMO", "demo", nameI18n, status, "fr", "EUR", settings);
    }
}
