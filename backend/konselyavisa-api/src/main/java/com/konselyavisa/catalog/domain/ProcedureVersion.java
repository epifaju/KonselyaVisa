package com.konselyavisa.catalog.domain;

import com.konselyavisa.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Getter
@Setter
@Entity
@Table(name = "procedure_versions")
public class ProcedureVersion extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "procedure_definition_id", nullable = false)
    private ProcedureDefinition procedureDefinition;

    @Column(name = "version_number", nullable = false)
    private int versionNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ProcedureVersionStatus status = ProcedureVersionStatus.DRAFT;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "eligibility_rules", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> eligibilityRules = new HashMap<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "document_requirements", nullable = false, columnDefinition = "jsonb")
    private List<Map<String, Object>> documentRequirements = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> pricing = new HashMap<>();

    @Column(name = "estimated_instruction_days")
    private Integer estimatedInstructionDays;

    @Column(name = "published_at")
    private Instant publishedAt;
}
