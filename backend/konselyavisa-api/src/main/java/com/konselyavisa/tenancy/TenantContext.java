package com.konselyavisa.tenancy;

import java.util.UUID;

/**
 * Thread-local tenant resolved from the JWT {@code organization_id} claim.
 * Cleared at the end of each HTTP request.
 */
public final class TenantContext {

    private static final ThreadLocal<UUID> CURRENT_ORGANIZATION = new ThreadLocal<>();
    private static final ThreadLocal<Boolean> PLATFORM_ADMIN = new ThreadLocal<>();

    private TenantContext() {}

    public static void setOrganizationId(UUID organizationId) {
        if (organizationId == null) {
            CURRENT_ORGANIZATION.remove();
        } else {
            CURRENT_ORGANIZATION.set(organizationId);
        }
    }

    public static UUID getOrganizationId() {
        return CURRENT_ORGANIZATION.get();
    }

    public static void setPlatformAdmin(boolean platformAdmin) {
        PLATFORM_ADMIN.set(platformAdmin);
    }

    public static boolean isPlatformAdmin() {
        return Boolean.TRUE.equals(PLATFORM_ADMIN.get());
    }

    public static void clear() {
        CURRENT_ORGANIZATION.remove();
        PLATFORM_ADMIN.remove();
    }
}
