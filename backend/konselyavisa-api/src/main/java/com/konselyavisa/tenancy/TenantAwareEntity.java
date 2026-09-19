package com.konselyavisa.tenancy;

import com.konselyavisa.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.FilterDef;
import org.hibernate.annotations.ParamDef;

@Getter
@Setter
@MappedSuperclass
@EntityListeners(TenantOrganizationListener.class)
@FilterDef(
        name = TenantFilters.FILTER_NAME,
        parameters = @ParamDef(name = TenantFilters.PARAM_NAME, type = UUID.class))
@Filter(name = TenantFilters.FILTER_NAME, condition = TenantFilters.CONDITION)
public abstract class TenantAwareEntity extends BaseEntity {

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;
}
