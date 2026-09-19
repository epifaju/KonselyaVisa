package com.konselyavisa.tenancy;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class TenantContextTest {

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void storesAndClearsOrganizationId() {
        UUID organizationId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        TenantContext.setOrganizationId(organizationId);
        assertThat(TenantContext.getOrganizationId()).isEqualTo(organizationId);
        TenantContext.clear();
        assertThat(TenantContext.getOrganizationId()).isNull();
        assertThat(TenantContext.isPlatformAdmin()).isFalse();
    }

    @Test
    void storesPlatformAdminFlag() {
        TenantContext.setPlatformAdmin(true);
        assertThat(TenantContext.isPlatformAdmin()).isTrue();
        TenantContext.clear();
        assertThat(TenantContext.isPlatformAdmin()).isFalse();
    }

    @Test
    void parseOrganizationIdRejectsBlank() {
        assertThat(TenantFilters.parseOrganizationId(" ")).isNull();
        assertThat(TenantFilters.parseOrganizationId("11111111-1111-1111-1111-111111111111"))
                .isEqualTo(UUID.fromString("11111111-1111-1111-1111-111111111111"));
    }
}
