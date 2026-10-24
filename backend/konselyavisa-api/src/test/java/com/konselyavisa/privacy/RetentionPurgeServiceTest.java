package com.konselyavisa.privacy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.konselyavisa.applicant.Applicant;
import com.konselyavisa.applicant.ApplicantRepository;
import com.konselyavisa.document.domain.CaseDocument;
import com.konselyavisa.document.persistence.CaseDocumentRepository;
import com.konselyavisa.dossier.CaseFile;
import com.konselyavisa.dossier.CaseFileRepository;
import com.konselyavisa.outbox.OutboxAppender;
import com.konselyavisa.privacy.domain.DataDeletionRequest;
import com.konselyavisa.privacy.domain.DataDeletionStatus;
import com.konselyavisa.privacy.persistence.DataDeletionRequestClaimer;
import com.konselyavisa.tenancy.TenantContext;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

@ExtendWith(MockitoExtension.class)
class RetentionPurgeServiceTest {

    @Mock
    private DataDeletionRequestClaimer claimer;

    @Mock
    private ApplicantRepository applicantRepository;

    @Mock
    private CaseFileRepository caseFileRepository;

    @Mock
    private CaseDocumentRepository caseDocumentRepository;

    @Mock
    private OutboxAppender outboxAppender;

    @Mock
    private PlatformTransactionManager transactionManager;

    @Mock
    private EntityManager entityManager;

    @Mock
    private Query nativeQuery;

    private KonselyaPrivacyProperties properties;
    private RetentionPurgeService service;

    @BeforeEach
    void setUp() {
        properties = new KonselyaPrivacyProperties();
        properties.setPurgeEnabled(true);
        properties.setPurgeBatchSize(10);
        org.mockito.Mockito.lenient()
                .when(transactionManager.getTransaction(any()))
                .thenReturn(new SimpleTransactionStatus());
        service = new RetentionPurgeService(
                properties,
                claimer,
                applicantRepository,
                caseFileRepository,
                caseDocumentRepository,
                outboxAppender,
                transactionManager);
        ReflectionTestUtils.setField(service, "entityManager", entityManager);
        TenantContext.clear();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void purgeDueReturnsZeroWhenDisabled() {
        properties.setPurgeEnabled(false);

        assertThat(service.purgeDue()).isZero();
        verify(claimer, never()).lockDue(any(), anyInt());
    }

    @Test
    void purgeDueAnonymizesApplicantDocumentsAndEmitsOutbox() {
        UUID orgId = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();
        UUID caseId = UUID.randomUUID();
        DataDeletionRequest request = new DataDeletionRequest();
        request.setId(requestId);
        request.setOrganizationId(orgId);
        request.setKeycloakSubject("sub-1");
        request.setStatus(DataDeletionStatus.PENDING);
        when(claimer.lockDue(any(Instant.class), eq(10))).thenReturn(List.of(request));

        CaseFile caseFile = new CaseFile();
        caseFile.setId(caseId);
        when(caseFileRepository.findAllByApplicant_KeycloakSubject(eq("sub-1"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(caseFile)));
        when(entityManager.createNativeQuery(anyString())).thenReturn(nativeQuery);
        when(nativeQuery.setParameter(eq("id"), eq(caseId))).thenReturn(nativeQuery);
        when(nativeQuery.executeUpdate()).thenReturn(1);

        CaseDocument document = new CaseDocument();
        document.setExtractedFieldsCipher(new byte[] {1, 2, 3});
        when(caseDocumentRepository.findByCaseFile_IdOrderByCreatedAtAsc(caseId)).thenReturn(List.of(document));

        Applicant applicant = new Applicant();
        applicant.setEmail("a@b.c");
        applicant.setDisplayName("Name");
        applicant.setFacts(new HashMap<>(Map.of("age", 30)));
        applicant.setKeycloakSubject("sub-1");
        when(applicantRepository.findByOrganizationIdAndKeycloakSubject(orgId, "sub-1"))
                .thenReturn(Optional.of(applicant));

        assertThat(service.purgeDue()).isEqualTo(1);
        assertThat(request.getStatus()).isEqualTo(DataDeletionStatus.COMPLETED);
        assertThat(request.getCompletedAt()).isNotNull();
        assertThat(document.getExtractedFieldsCipher()).isNull();
        assertThat(applicant.getEmail()).isNull();
        assertThat(applicant.getDisplayName()).isEqualTo("anonymized");
        assertThat(applicant.getKeycloakSubject()).isNull();
        verify(applicantRepository).saveAndFlush(applicant);
        verify(outboxAppender).appendDataDeletionCompleted(eq(requestId), any());
        verify(nativeQuery).executeUpdate();
        assertThat(TenantContext.isPlatformAdmin()).isFalse();
        assertThat(TenantContext.getOrganizationId()).isNull();
    }

    @Test
    void purgeDueRestoresTenantContextAfterRun() {
        TenantContext.setPlatformAdmin(false);
        TenantContext.setOrganizationId(UUID.randomUUID());
        when(claimer.lockDue(any(Instant.class), anyInt())).thenReturn(List.of());

        assertThat(service.purgeDue()).isZero();
        assertThat(TenantContext.isPlatformAdmin()).isFalse();
        assertThat(TenantContext.getOrganizationId()).isNotNull();
    }
}
