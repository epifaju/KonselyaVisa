package com.konselyavisa.tenancy;

import java.util.UUID;

public final class TenantFilters {

    public static final String FILTER_NAME = "tenantFilter";
    public static final String PARAM_NAME = "organizationId";
    public static final String CONDITION = "organization_id = :" + PARAM_NAME;

    private TenantFilters() {}

    public static UUID parseOrganizationId(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return UUID.fromString(raw.trim());
    }
}
