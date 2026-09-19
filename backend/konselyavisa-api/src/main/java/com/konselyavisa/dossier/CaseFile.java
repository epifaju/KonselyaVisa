package com.konselyavisa.dossier;

import com.konselyavisa.applicant.Applicant;
import com.konselyavisa.catalog.domain.ProcedureDefinition;
import com.konselyavisa.catalog.domain.ProcedureVersion;
import com.konselyavisa.tenancy.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.HashMap;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Getter
@Setter
@Entity
@Table(name = "cases")
public class CaseFile extends TenantAwareEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "applicant_id", nullable = false, updatable = false)
    private Applicant applicant;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "procedure_definition_id", nullable = false, updatable = false)
    private ProcedureDefinition procedureDefinition;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "procedure_version_id", nullable = false, updatable = false)
    private ProcedureVersion procedureVersion;

    @Column(nullable = false, length = 40, updatable = false)
    private String reference;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private CaseStatus status = CaseStatus.CREATED;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "applicant_facts", nullable = false, columnDefinition = "jsonb", updatable = false)
    private Map<String, Object> applicantFacts = new HashMap<>();

    @Column(name = "eligibility_passed", nullable = false, updatable = false)
    private boolean eligibilityPassed;

    @Column(name = "created_by_role", length = 40, updatable = false)
    private String createdByRole;

    @Column(name = "created_by_label", length = 120, updatable = false)
    private String createdByLabel;
}
