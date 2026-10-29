package com.konselyavisa.guest;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PublicSiteSettingsTest {

    @Test
    void languagesNormalizeDeduplicateAndFallBack() {
        assertThat(PublicSiteSettings.languages(null, null)).containsExactly("fr");
        assertThat(PublicSiteSettings.languages(Map.of(), " PT ")).containsExactly("pt");
        assertThat(PublicSiteSettings.languages(Map.of("activeLanguages", "fr"), "en")).containsExactly("en");
        assertThat(PublicSiteSettings.languages(
                        Map.of("activeLanguages", Arrays.asList(" FR ", "", null, "fr", "EN")), "pt"))
                .containsExactly("fr", "en");
    }

    @Test
    void i18nAcceptsMapsStringsAndMissingValues() {
        assertThat(PublicSiteSettings.i18n(null, "address")).isEmpty();
        assertThat(PublicSiteSettings.i18n(Map.of("address", "  "), "address")).isEmpty();
        assertThat(PublicSiteSettings.i18n(Map.of("address", "Paris"), "address"))
                .containsEntry("fr", "Paris")
                .containsEntry("pt", "Paris")
                .containsEntry("en", "Paris");
        Map<String, Object> address = new LinkedHashMap<>();
        address.put("fr", "Paris");
        address.put("en", 12);
        address.put("pt", null);
        assertThat(PublicSiteSettings.i18n(Map.of("address", address), "address"))
                .containsEntry("fr", "Paris")
                .containsEntry("en", "12")
                .doesNotContainKey("pt");
    }

    @Test
    void textReturnsNullWhenAbsent() {
        assertThat(PublicSiteSettings.text(null, "phone")).isNull();
        assertThat(PublicSiteSettings.text(Map.of(), "phone")).isNull();
        assertThat(PublicSiteSettings.text(Map.of("phone", 221), "phone")).isEqualTo("221");
    }
}
