package com.konselyavisa.guest;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class PublicSiteSettings {

    private PublicSiteSettings() {}

    static List<String> languages(Map<String, Object> settings, String defaultLocale) {
        Object raw = settings == null ? null : settings.get("activeLanguages");
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

    static Map<String, String> i18n(Map<String, Object> settings, String key) {
        if (settings == null) {
            return Map.of();
        }
        Object raw = settings.get(key);
        if (raw instanceof Map<?, ?> map) {
            Map<String, String> values = new LinkedHashMap<>();
            map.forEach((locale, value) -> {
                if (locale != null && value != null) {
                    values.put(locale.toString(), value.toString());
                }
            });
            return values;
        }
        if (raw instanceof String text && !text.isBlank()) {
            return Map.of("fr", text, "pt", text, "en", text);
        }
        return Map.of();
    }

    static String text(Map<String, Object> settings, String key) {
        if (settings == null) {
            return null;
        }
        Object raw = settings.get(key);
        return raw == null ? null : raw.toString();
    }
}
