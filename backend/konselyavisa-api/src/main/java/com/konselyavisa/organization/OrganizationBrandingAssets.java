package com.konselyavisa.organization;

import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * White-label logo / favicon keys in MinIO and {@code organization_settings.settings}.
 */
public final class OrganizationBrandingAssets {

    public static final String LOGO = "logo";
    public static final String FAVICON = "favicon";
    public static final long LOGO_MAX_BYTES = 512 * 1024;
    public static final long FAVICON_MAX_BYTES = 128 * 1024;

    private static final Set<String> ALLOWED_TYPES = Set.of(
            "image/png", "image/jpeg", "image/webp", "image/x-icon", "image/vnd.microsoft.icon");

    private OrganizationBrandingAssets() {}

    public static boolean isAllowedContentType(String contentType) {
        return contentType != null && ALLOWED_TYPES.contains(contentType.toLowerCase(Locale.ROOT));
    }

    public static long maxBytes(String asset) {
        return FAVICON.equals(asset) ? FAVICON_MAX_BYTES : LOGO_MAX_BYTES;
    }

    public static String storageKey(UUID organizationId, String asset) {
        return "branding/" + organizationId + "/" + asset;
    }

    public static String contentTypeKey(String asset) {
        return asset + "ContentType";
    }

    public static String contentType(Map<String, Object> settings, String asset) {
        if (settings == null) {
            return null;
        }
        Object raw = settings.get(contentTypeKey(asset));
        if (raw == null) {
            return null;
        }
        String value = raw.toString().trim();
        return value.isEmpty() ? null : value;
    }

    public static boolean hasAsset(Map<String, Object> settings, String asset) {
        return contentType(settings, asset) != null;
    }

    public static String publicPath(UUID organizationId, String asset) {
        return "/api/v1/public/org/branding/" + asset + "?organizationId=" + organizationId;
    }
}
