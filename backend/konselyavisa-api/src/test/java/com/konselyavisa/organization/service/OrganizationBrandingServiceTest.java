package com.konselyavisa.organization.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.konselyavisa.common.exception.BusinessException;
import com.konselyavisa.organization.OrganizationBrandingAssets;
import com.konselyavisa.organization.api.OrganizationBrandingResponse;
import com.konselyavisa.organization.api.OrganizationResponse;
import com.konselyavisa.organization.domain.OrganizationStatus;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OrganizationBrandingServiceTest {

    @Mock
    private OrganizationService organizationService;

    private OrganizationBrandingService service;

    @BeforeEach
    void setUp() {
        service = new OrganizationBrandingService(organizationService);
    }

    @Test
    void currentThrowsWhenOrganizationNotActive() {
        UUID orgId = UUID.randomUUID();
        when(organizationService.getCurrent())
                .thenReturn(org(orgId, OrganizationStatus.SUSPENDED, "fr", Map.of()));

        assertThatThrownBy(() -> service.current())
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getMessageKey())
                .isEqualTo("error.organization.not_found");
    }

    @Test
    void currentBuildsLogoAndFaviconPathsWhenAssetsPresent() {
        UUID orgId = UUID.randomUUID();
        when(organizationService.getCurrent())
                .thenReturn(org(
                        orgId,
                        OrganizationStatus.ACTIVE,
                        "pt",
                        Map.of(
                                "brandColor",
                                "#abc",
                                "domain",
                                "visa.example",
                                "activeLanguages",
                                List.of("FR", "pt", "fr"),
                                OrganizationBrandingAssets.contentTypeKey(OrganizationBrandingAssets.LOGO),
                                "image/png",
                                OrganizationBrandingAssets.contentTypeKey(OrganizationBrandingAssets.FAVICON),
                                "image/x-icon")));

        OrganizationBrandingResponse response = service.current();

        assertThat(response.organizationId()).isEqualTo(orgId);
        assertThat(response.brandColor()).isEqualTo("#AABBCC");
        assertThat(response.domain()).isEqualTo("visa.example");
        assertThat(response.activeLanguages()).containsExactly("fr", "pt");
        assertThat(response.logoUrl())
                .isEqualTo(OrganizationBrandingAssets.publicPath(orgId, OrganizationBrandingAssets.LOGO));
        assertThat(response.faviconUrl())
                .isEqualTo(OrganizationBrandingAssets.publicPath(orgId, OrganizationBrandingAssets.FAVICON));
    }

    @Test
    void currentOmitsAssetUrlsWhenMissingAndFallsBackToDefaultLocale() {
        UUID orgId = UUID.randomUUID();
        when(organizationService.getCurrent())
                .thenReturn(org(orgId, OrganizationStatus.ACTIVE, "EN", Map.of()));

        OrganizationBrandingResponse response = service.current();

        assertThat(response.logoUrl()).isNull();
        assertThat(response.faviconUrl()).isNull();
        assertThat(response.activeLanguages()).containsExactly("en");
        assertThat(response.brandColor()).isNull();
    }

    @Test
    void languagesFallsBackToFrWhenNoLocale() {
        assertThat(OrganizationBrandingService.languages(Map.of(), "  ")).containsExactly("fr");
    }

    private static OrganizationResponse org(
            UUID id, OrganizationStatus status, String locale, Map<String, Object> settings) {
        return new OrganizationResponse(
                id, "DEMO", "demo", Map.of("fr", "Demo"), status, locale, "EUR", settings);
    }
}
