package com.konselyavisa.document.domain;

import com.konselyavisa.dossier.CaseFile;
import com.konselyavisa.tenancy.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "document_upload_refusals")
public class DocumentUploadRefusal extends TenantAwareEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "case_id", nullable = false, updatable = false)
    private CaseFile caseFile;

    @Column(name = "requirement_code", length = 50, updatable = false)
    private String requirementCode;

    @Column(name = "reason_key", nullable = false, length = 120, updatable = false)
    private String reasonKey;

    @Column(name = "content_type", length = 100, updatable = false)
    private String contentType;

    @Column(name = "size_bytes", updatable = false)
    private Long sizeBytes;

    @Column(length = 64, updatable = false)
    private String sha256;

    @Column(name = "original_filename", length = 255, updatable = false)
    private String originalFilename;

    @Column(name = "attempted_by", length = 100, updatable = false)
    private String attemptedBy;
}
