package com.konselyavisa.organization;

import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Parses white-label keys from {@code organization_settings.settings} (PRD): {@code brandColor},
 * {@code domain}.
 */
public final class OrganizationBrandingSettings {

    private static final Pattern HEX_COLOR = Pattern.compile("^#([0-9a-fA-F]{3}|[0-9a-fA-F]{6})$");
    private static final Pattern DOMAIN = Pattern.compile(
            "^(?=.{1,253}$)(?!-)[a-z0-9-]+(\\.[a-z0-9-]+)*\\.?$", Pattern.CASE_INSENSITIVE);

    private OrganizationBrandingSettings() {}

    public static String brandColor(Map<String, Object> settings) {
        String raw = text(settings, "brandColor");
        if (raw == null) {
            return null;
        }
        String normalized = raw.startsWith("#") ? raw : "#" + raw;
        if (!HEX_COLOR.matcher(normalized).matches()) {
            return null;
        }
        return expandHex(normalized.toUpperCase(Locale.ROOT));
    }

    public static String domain(Map<String, Object> settings) {
        String raw = text(settings, "domain");
        if (raw == null) {
            return null;
        }
        String host = stripHostNoise(raw);
        if (host == null || !DOMAIN.matcher(host).matches()) {
            return null;
        }
        if (host.endsWith(".")) {
            host = host.substring(0, host.length() - 1);
        }
        return host.toLowerCase(Locale.ROOT);
    }

    public static String normalizeDomainHint(String domainOrHost) {
        if (domainOrHost == null || domainOrHost.isBlank()) {
            return null;
        }
        String host = stripHostNoise(domainOrHost.trim());
        if (host == null || host.isBlank()) {
            return null;
        }
        if (host.endsWith(".")) {
            host = host.substring(0, host.length() - 1);
        }
        return host.toLowerCase(Locale.ROOT);
    }

    private static String text(Map<String, Object> settings, String key) {
        if (settings == null) {
            return null;
        }
        Object raw = settings.get(key);
        if (raw == null) {
            return null;
        }
        String value = raw.toString().trim();
        return value.isEmpty() ? null : value;
    }

    private static String stripHostNoise(String value) {
        String host = value.trim();
        int scheme = host.indexOf("://");
        if (scheme >= 0) {
            host = host.substring(scheme + 3);
        }
        int slash = host.indexOf('/');
        if (slash >= 0) {
            host = host.substring(0, slash);
        }
        int at = host.lastIndexOf('@');
        if (at >= 0) {
            host = host.substring(at + 1);
        }
        if (host.startsWith("[")) {
            int end = host.indexOf(']');
            if (end > 0) {
                host = host.substring(1, end);
            }
        } else {
            int colon = host.lastIndexOf(':');
            if (colon > 0 && host.indexOf(':') == colon) {
                host = host.substring(0, colon);
            }
        }
        return host.isBlank() ? null : host;
    }

    private static String expandHex(String hex) {
        if (hex.length() == 4) {
            char r = hex.charAt(1);
            char g = hex.charAt(2);
            char b = hex.charAt(3);
            return "#" + r + r + g + g + b + b;
        }
        return hex;
    }
}
