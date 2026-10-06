package com.konselyavisa.privacy.domain;

import com.konselyavisa.tenancy.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Getter
@Setter
@Entity
@Table(name = "organization_dpa_agreements")
public class OrganizationDpaAgreement extends TenantAwareEntity {

    @Column(nullable = false, length = 40)
    private String version;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrganizationDpaStatus status = OrganizationDpaStatus.ACTIVE;

    @Column(name = "retention_days", nullable = false)
    private int retentionDays;

    @Column(name = "processor_legal_name", nullable = false, length = 200)
    private String processorLegalName;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "controller_name_i18n", nullable = false, columnDefinition = "jsonb")
    private Map<String, String> controllerNameI18n = new HashMap<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "summary_i18n", nullable = false, columnDefinition = "jsonb")
    private Map<String, String> summaryI18n = new HashMap<>();

    @Column(name = "document_uri", length = 500)
    private String documentUri;

    @Column(name = "accepted_at", nullable = false)
    private Instant acceptedAt;

    @Column(name = "accepted_by_label", length = 120)
    private String acceptedByLabel;
}
