package com.konselyavisa.document.domain;

import com.konselyavisa.dossier.CaseFile;
import com.konselyavisa.tenancy.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Getter
@Setter
@Entity
@Table(name = "documents")
public class CaseDocument extends TenantAwareEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "case_id", nullable = false, updatable = false)
    private CaseFile caseFile;

    @Column(name = "requirement_code", nullable = false, length = 50, updatable = false)
    private String requirementCode;

    @Column(name = "original_filename", nullable = false, length = 255)
    private String originalFilename;

    @Column(name = "content_type", nullable = false, length = 100)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(nullable = false, length = 64, updatable = false)
    private String sha256;

    @Column(name = "storage_key", nullable = false, length = 500, updatable = false)
    private String storageKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private DocumentStatus status = DocumentStatus.UPLOADED;

    @Column(name = "duplicate_hash", nullable = false)
    private boolean duplicateHash;

    @Column(name = "review_message_key", length = 120)
    private String reviewMessageKey;

    @Column(name = "extracted_fields_cipher", columnDefinition = "bytea")
    private byte[] extractedFieldsCipher;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "document_validations", nullable = false, columnDefinition = "jsonb")
    private Map<String, String> documentValidations = new HashMap<>();

    @Column(name = "ai_confidence", precision = 5, scale = 4)
    private BigDecimal aiConfidence;

    @Enumerated(EnumType.STRING)
    @Column(name = "extraction_source", length = 20)
    private ExtractionSource extractionSource;
}
