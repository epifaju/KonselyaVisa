package com.konselyavisa.organization.domain;

import com.konselyavisa.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Global rows ({@code organization_id} null) plus per-org overrides. Hibernate tenant
 * filter is intentionally omitted: a standard {@code organization_id = :org} filter would
 * hide global flags. Isolation is RLS (null org or current org) plus keyed lookups.
 */
@Getter
@Setter
@Entity
@Table(name = "feature_flags")
public class FeatureFlag extends BaseEntity {

    @Column(name = "flag_key", nullable = false, length = 100)
    private String key;

    @Column(name = "organization_id")
    private UUID organizationId;

    @Column(nullable = false)
    private boolean enabled;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "value_json", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> valueJson = new HashMap<>();
}
