package com.konselyavisa.privacy.domain;

import com.konselyavisa.applicant.Applicant;
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
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "privacy_consents")
public class PrivacyConsent extends TenantAwareEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "applicant_id")
    private Applicant applicant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "case_id")
    private CaseFile caseFile;

    @Column(name = "keycloak_subject", length = 100)
    private String keycloakSubject;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private PrivacyConsentPurpose purpose = PrivacyConsentPurpose.CASE_DEPOSIT;

    @Column(name = "notice_version", nullable = false, length = 20)
    private String noticeVersion;

    @Column(nullable = false, length = 8)
    private String locale;

    @Column(name = "text_accepted", nullable = false, columnDefinition = "text")
    private String textAccepted;

    @Column(name = "accepted_at", nullable = false)
    private Instant acceptedAt;
}
