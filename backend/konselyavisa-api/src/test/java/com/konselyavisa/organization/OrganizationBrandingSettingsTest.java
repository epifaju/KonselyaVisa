package com.konselyavisa.organization;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

class OrganizationBrandingSettingsTest {

    @Test
    void brandColorAcceptsHexAndExpandsShortForm() {
        assertThat(OrganizationBrandingSettings.brandColor(Map.of("brandColor", "#0B5D3B")))
                .isEqualTo("#0B5D3B");
        assertThat(OrganizationBrandingSettings.brandColor(Map.of("brandColor", "#0b5")))
                .isEqualTo("#00BB55");
        assertThat(OrganizationBrandingSettings.brandColor(Map.of("brandColor", "0B5D3B"))).isEqualTo("#0B5D3B");
        assertThat(OrganizationBrandingSettings.brandColor(Map.of("brandColor", "not-a-color"))).isNull();
        assertThat(OrganizationBrandingSettings.brandColor(Map.of("brandColor", "  "))).isNull();
        assertThat(OrganizationBrandingSettings.brandColor(Map.of())).isNull();
        assertThat(OrganizationBrandingSettings.brandColor(null)).isNull();
    }

    @Test
    void domainNormalizesHostAndRejectsPaths() {
        assertThat(OrganizationBrandingSettings.domain(Map.of("domain", "Visa.Acme.COM")))
                .isEqualTo("visa.acme.com");
        assertThat(OrganizationBrandingSettings.domain(Map.of("domain", "https://visa.acme.com/path")))
                .isEqualTo("visa.acme.com");
        assertThat(OrganizationBrandingSettings.normalizeDomainHint("visa.acme.com:443"))
                .isEqualTo("visa.acme.com");
        assertThat(OrganizationBrandingSettings.domain(Map.of("domain", "https://agent@Visa.Acme.com./fr")))
                .isEqualTo("visa.acme.com");
        assertThat(OrganizationBrandingSettings.domain(Map.of("domain", "visa.acme.com.")))
                .isEqualTo("visa.acme.com");
        assertThat(OrganizationBrandingSettings.domain(Map.of("domain", "bad domain"))).isNull();
        assertThat(OrganizationBrandingSettings.domain(null)).isNull();
        assertThat(OrganizationBrandingSettings.normalizeDomainHint("  ")).isNull();
        assertThat(OrganizationBrandingSettings.normalizeDomainHint(null)).isNull();
        assertThat(OrganizationBrandingSettings.normalizeDomainHint("https://[2001:db8::1]/path"))
                .isEqualTo("2001:db8::1");
    }
}
