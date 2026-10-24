package com.konselyavisa.privacy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.konselyavisa.applicant.ApplicantRepository;
import com.konselyavisa.common.exception.BusinessException;
import com.konselyavisa.document.domain.CaseDocument;
import com.konselyavisa.document.domain.DocumentStatus;
import com.konselyavisa.document.persistence.CaseDocumentRepository;
import com.konselyavisa.dossier.CaseFile;
import com.konselyavisa.dossier.CaseFileRepository;
import com.konselyavisa.outbox.OutboxAppender;
import com.konselyavisa.privacy.api.DataDeletionRequestResponse;
import com.konselyavisa.privacy.api.PersonalDataExportResponse;
import com.konselyavisa.privacy.domain.DataDeletionRequest;
import com.konselyavisa.privacy.domain.DataDeletionStatus;
import com.konselyavisa.privacy.domain.OrganizationDpaAgreement;
import com.konselyavisa.privacy.domain.PrivacyConsent;
import com.konselyavisa.privacy.domain.PrivacyConsentPurpose;
import com.konselyavisa.privacy.persistence.DataDeletionRequestRepository;
import com.konselyavisa.privacy.persistence.PrivacyConsentRepository;
import com.konselyavisa.tenancy.TenantContext;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

@ExtendWith(MockitoExtension.class)
class PersonalDataRightsServiceTest {

    @Mock
    private ApplicantRepository applicantRepository;

    @Mock
    private CaseFileRepository caseFileRepository;

    @Mock
    private CaseDocumentRepository caseDocumentRepository;

    @Mock
    private PrivacyConsentRepository privacyConsentRepository;

    @Mock
    private DataDeletionRequestRepository dataDeletionRequestRepository;

    @Mock
    private OrganizationDpaService organizationDpaService;

    @Mock
    private OutboxAppender outboxAppender;

    private KonselyaPrivacyProperties properties;
    private PersonalDataRightsService service;
    private UUID orgId;

    @BeforeEach
    void setUp() {
        orgId = UUID.randomUUID();
        properties = new KonselyaPrivacyProperties();
        properties.setAnonymizeAfter(Duration.ofDays(10));
        service = new PersonalDataRightsService(
                applicantRepository,
                caseFileRepository,
                caseDocumentRepository,
                privacyConsentRepository,
                dataDeletionRequestRepository,
                organizationDpaService,
                outboxAppender,
                properties);
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    void exportMineRequiresSubject() {
        assertThatThrownBy(() -> service.exportMine())
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getMessageKey())
                .isEqualTo("error.access.denied");
    }

    @Test
    void exportMineRequiresOrganization() {
        authenticateAsCitizen("privacy-sub-1");
        assertThatThrownBy(() -> service.exportMine())
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getMessageKey())
                .isEqualTo("error.organization.missing");
    }

    @Test
    void exportMineReturnsEmptyProfileWhenNoApplicant() {
        authenticateAsCitizen("privacy-sub-1");
        TenantContext.setOrganizationId(orgId);
        when(applicantRepository.findByOrganizationIdAndKeycloakSubject(orgId, "privacy-sub-1"))
                .thenReturn(Optional.empty());
        when(caseFileRepository.findAllByApplicant_KeycloakSubject(eq("privacy-sub-1"), any(Pageable.class)))
                .thenReturn(Page.empty());
        when(privacyConsentRepository.findByKeycloakSubjectOrderByAcceptedAtDesc("privacy-sub-1"))
                .thenReturn(List.of());
        when(dataDeletionRequestRepository.findByKeycloakSubjectOrderByRequestedAtDesc("privacy-sub-1"))
                .thenReturn(List.of());

        PersonalDataExportResponse export = service.exportMine();

        assertThat(export.keycloakSubject()).isEqualTo("privacy-sub-1");
        assertThat(export.applicant().email()).isNull();
        assertThat(export.applicant().facts()).isEmpty();
        assertThat(export.cases()).isEmpty();
        assertThat(export.consents()).isEmpty();
        assertThat(export.deletionRequests()).isEmpty();
    }

    @Test
    void requestDeletionIsIdempotentWhenPendingExists() {
        authenticateAsCitizen("privacy-sub-1");
        TenantContext.setOrganizationId(orgId);
        UUID existingId = UUID.randomUUID();
        Instant requestedAt = Instant.parse("2026-01-01T00:00:00Z");
        Instant scheduled = Instant.parse("2026-01-31T00:00:00Z");
        DataDeletionRequest existing = new DataDeletionRequest();
        existing.setId(existingId);
        existing.setKeycloakSubject("privacy-sub-1");
        existing.setStatus(DataDeletionStatus.PENDING);
        existing.setRequestedAt(requestedAt);
        existing.setScheduledAnonymizeAt(scheduled);
        when(dataDeletionRequestRepository.findFirstByKeycloakSubjectAndStatusOrderByRequestedAtDesc(
                        "privacy-sub-1", DataDeletionStatus.PENDING))
                .thenReturn(Optional.of(existing));

        DataDeletionRequestResponse response = service.requestDeletion();

        assertThat(response.id()).isEqualTo(existingId);
        assertThat(response.status()).isEqualTo(DataDeletionStatus.PENDING);
        verify(dataDeletionRequestRepository, never()).saveAndFlush(any());
        verify(outboxAppender, never()).appendDataDeletionRequested(any(), any());
    }

    @Test
    void requestDeletionUsesDpaRetentionAndEmitsOutbox() {
        authenticateAsCitizen("privacy-sub-1");
        TenantContext.setOrganizationId(orgId);
        when(dataDeletionRequestRepository.findFirstByKeycloakSubjectAndStatusOrderByRequestedAtDesc(
                        "privacy-sub-1", DataDeletionStatus.PENDING))
                .thenReturn(Optional.empty());
        OrganizationDpaAgreement dpa = new OrganizationDpaAgreement();
        dpa.setRetentionDays(30);
        when(organizationDpaService.findActiveEntity(orgId)).thenReturn(Optional.of(dpa));
        when(dataDeletionRequestRepository.saveAndFlush(any(DataDeletionRequest.class))).thenAnswer(inv -> {
            DataDeletionRequest request = inv.getArgument(0);
            request.setId(UUID.randomUUID());
            return request;
        });

        Instant before = Instant.now();
        DataDeletionRequestResponse response = service.requestDeletion();
        Instant after = Instant.now();

        assertThat(response.status()).isEqualTo(DataDeletionStatus.PENDING);
        assertThat(response.scheduledAnonymizeAt())
                .isCloseTo(before.plus(Duration.ofDays(30)), within(5, ChronoUnit.SECONDS));
        assertThat(response.scheduledAnonymizeAt())
                .isCloseTo(after.plus(Duration.ofDays(30)), within(5, ChronoUnit.SECONDS));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> payload = ArgumentCaptor.forClass(Map.class);
        verify(outboxAppender).appendDataDeletionRequested(eq(response.id()), payload.capture());
        assertThat(payload.getValue())
                .containsEntry("requestId", response.id().toString())
                .containsKey("scheduledAnonymizeAt");
    }

    @Test
    void requestDeletionFallsBackToPrivacyPropertiesWhenNoDpa() {
        authenticateAsCitizen("privacy-sub-1");
        TenantContext.setOrganizationId(orgId);
        when(dataDeletionRequestRepository.findFirstByKeycloakSubjectAndStatusOrderByRequestedAtDesc(
                        "privacy-sub-1", DataDeletionStatus.PENDING))
                .thenReturn(Optional.empty());
        when(organizationDpaService.findActiveEntity(orgId)).thenReturn(Optional.empty());
        when(dataDeletionRequestRepository.saveAndFlush(any(DataDeletionRequest.class))).thenAnswer(inv -> {
            DataDeletionRequest request = inv.getArgument(0);
            request.setId(UUID.randomUUID());
            return request;
        });

        Instant before = Instant.now();
        DataDeletionRequestResponse response = service.requestDeletion();

        assertThat(response.scheduledAnonymizeAt())
                .isCloseTo(before.plus(Duration.ofDays(10)), within(5, ChronoUnit.SECONDS));
    }

    @Test
    void requestDeletionRequiresSubjectAndOrganization() {
        assertThatThrownBy(() -> service.requestDeletion())
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getMessageKey())
                .isEqualTo("error.access.denied");

        authenticateAsCitizen("privacy-sub-1");
        assertThatThrownBy(() -> service.requestDeletion())
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getMessageKey())
                .isEqualTo("error.organization.missing");
    }

    @Test
    void requestDeletionUsesDefaultRetentionWhenPropertyNullOrNegative() {
        authenticateAsCitizen("privacy-sub-1");
        TenantContext.setOrganizationId(orgId);
        properties.setAnonymizeAfter(null);
        when(dataDeletionRequestRepository.findFirstByKeycloakSubjectAndStatusOrderByRequestedAtDesc(
                        "privacy-sub-1", DataDeletionStatus.PENDING))
                .thenReturn(Optional.empty());
        when(organizationDpaService.findActiveEntity(orgId)).thenReturn(Optional.empty());
        when(dataDeletionRequestRepository.saveAndFlush(any(DataDeletionRequest.class))).thenAnswer(inv -> {
            DataDeletionRequest request = inv.getArgument(0);
            request.setId(UUID.randomUUID());
            return request;
        });

        Instant before = Instant.now();
        DataDeletionRequestResponse response = service.requestDeletion();

        assertThat(response.scheduledAnonymizeAt())
                .isCloseTo(before.plus(Duration.ofDays(1825)), within(5, ChronoUnit.SECONDS));
    }

    @Test
    void exportMineAggregatesCasesDocumentsConsentsAndDeletions() {
        authenticateAsCitizen("privacy-sub-1");
        TenantContext.setOrganizationId(orgId);

        com.konselyavisa.applicant.Applicant applicant = new com.konselyavisa.applicant.Applicant();
        applicant.setEmail("a@b.c");
        applicant.setDisplayName("Ada");
        applicant.setFacts(Map.of("age", 30));
        when(applicantRepository.findByOrganizationIdAndKeycloakSubject(orgId, "privacy-sub-1"))
                .thenReturn(Optional.of(applicant));

        UUID caseId = UUID.randomUUID();
        com.konselyavisa.catalog.domain.ProcedureDefinition procedure =
                new com.konselyavisa.catalog.domain.ProcedureDefinition();
        procedure.setCode("VISA_TOURISM");
        CaseFile caseFile = new CaseFile();
        caseFile.setId(caseId);
        caseFile.setReference("KV-1");
        caseFile.setStatus(com.konselyavisa.dossier.CaseStatus.IN_PROGRESS);
        caseFile.setProcedureDefinition(procedure);
        caseFile.setCreatedAt(Instant.parse("2026-01-01T00:00:00Z"));
        caseFile.setUpdatedAt(Instant.parse("2026-01-02T00:00:00Z"));
        when(caseFileRepository.findAllByApplicant_KeycloakSubject(eq("privacy-sub-1"), any(Pageable.class)))
                .thenReturn(new org.springframework.data.domain.PageImpl<>(List.of(caseFile)));

        CaseDocument document = new CaseDocument();
        document.setRequirementCode("PASSPORT");
        document.setStatus(DocumentStatus.ACCEPTED);
        document.setCreatedAt(Instant.parse("2026-01-01T12:00:00Z"));
        document.setCaseFile(caseFile);
        when(caseDocumentRepository.findByCaseFile_IdIn(List.of(caseId))).thenReturn(List.of(document));

        PrivacyConsent consent = new PrivacyConsent();
        consent.setPurpose(PrivacyConsentPurpose.CASE_DEPOSIT);
        consent.setNoticeVersion("1");
        consent.setLocale("fr");
        consent.setAcceptedAt(Instant.parse("2026-01-01T00:00:00Z"));
        when(privacyConsentRepository.findByKeycloakSubjectOrderByAcceptedAtDesc("privacy-sub-1"))
                .thenReturn(List.of(consent));

        DataDeletionRequest deletion = new DataDeletionRequest();
        deletion.setStatus(DataDeletionStatus.PENDING);
        deletion.setRequestedAt(Instant.parse("2026-02-01T00:00:00Z"));
        deletion.setScheduledAnonymizeAt(Instant.parse("2026-03-01T00:00:00Z"));
        when(dataDeletionRequestRepository.findByKeycloakSubjectOrderByRequestedAtDesc("privacy-sub-1"))
                .thenReturn(List.of(deletion));

        PersonalDataExportResponse export = service.exportMine();

        assertThat(export.applicant().email()).isEqualTo("a@b.c");
        assertThat(export.applicant().displayName()).isEqualTo("Ada");
        assertThat(export.cases()).hasSize(1);
        assertThat(export.cases().get(0).reference()).isEqualTo("KV-1");
        assertThat(export.cases().get(0).procedureCode()).isEqualTo("VISA_TOURISM");
        assertThat(export.cases().get(0).documents()).hasSize(1);
        assertThat(export.cases().get(0).documents().get(0).requirementCode()).isEqualTo("PASSPORT");
        assertThat(export.consents()).hasSize(1);
        assertThat(export.consents().get(0).purpose()).isEqualTo("CASE_DEPOSIT");
        assertThat(export.deletionRequests()).hasSize(1);
        assertThat(export.deletionRequests().get(0).status()).isEqualTo("PENDING");
    }

    private static void authenticateAsCitizen(String subject) {
        Jwt jwt = Jwt.withTokenValue("test")
                .header("alg", "none")
                .subject(subject)
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .claim("preferred_username", "citizen.dev")
                .build();
        SecurityContextHolder.getContext()
                .setAuthentication(new JwtAuthenticationToken(
                        jwt, List.of(new SimpleGrantedAuthority("ROLE_CITIZEN"))));
    }
}
