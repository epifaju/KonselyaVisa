package com.konselyavisa.tenancy;

import com.konselyavisa.common.exception.BusinessException;
import jakarta.persistence.PrePersist;
import java.util.UUID;

public class TenantOrganizationListener {

    @PrePersist
    public void assignOrganization(TenantAwareEntity entity) {
        if (entity.getOrganizationId() != null) {
            return;
        }
        UUID organizationId = TenantContext.getOrganizationId();
        if (organizationId == null) {
            throw BusinessException.forbidden("error.organization.missing");
        }
        entity.setOrganizationId(organizationId);
    }
}
