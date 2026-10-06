package com.konselyavisa.privacy;

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
import jakarta.persistence.PersistenceContext;
import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class RetentionPurgeService {

    private static final Logger log = LoggerFactory.getLogger(RetentionPurgeService.class);

    private final KonselyaPrivacyProperties properties;
    private final DataDeletionRequestClaimer claimer;
    private final ApplicantRepository applicantRepository;
    private final CaseFileRepository caseFileRepository;
    private final CaseDocumentRepository caseDocumentRepository;
    private final OutboxAppender outboxAppender;
    private final TransactionTemplate transactionTemplate;

    @PersistenceContext
    private EntityManager entityManager;

    public RetentionPurgeService(
            KonselyaPrivacyProperties properties,
            DataDeletionRequestClaimer claimer,
            ApplicantRepository applicantRepository,
            CaseFileRepository caseFileRepository,
            CaseDocumentRepository caseDocumentRepository,
            OutboxAppender outboxAppender,
            PlatformTransactionManager transactionManager) {
        this.properties = properties;
        this.claimer = claimer;
        this.applicantRepository = applicantRepository;
        this.caseFileRepository = caseFileRepository;
        this.caseDocumentRepository = caseDocumentRepository;
        this.outboxAppender = outboxAppender;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public int purgeDue() {
        if (!properties.isPurgeEnabled()) {
            return 0;
        }
        boolean previousAdmin = TenantContext.isPlatformAdmin();
        UUID previousOrg = TenantContext.getOrganizationId();
        TenantContext.setPlatformAdmin(true);
        TenantContext.setOrganizationId(null);
        try {
            Integer purged = transactionTemplate.execute(status -> {
                List<DataDeletionRequest> due = claimer.lockDue(Instant.now(), properties.getPurgeBatchSize());
                int completed = 0;
                for (DataDeletionRequest request : due) {
                    anonymize(request);
                    completed++;
                }
                return completed;
            });
            return purged == null ? 0 : purged;
        } finally {
            TenantContext.setPlatformAdmin(previousAdmin);
            TenantContext.setOrganizationId(previousOrg);
        }
    }

    private void anonymize(DataDeletionRequest request) {
        UUID organizationId = request.getOrganizationId();
        String subject = request.getKeycloakSubject();
        TenantContext.setOrganizationId(organizationId);
        try {
            List<CaseFile> cases = caseFileRepository
                    .findAllByApplicant_KeycloakSubject(subject, Pageable.unpaged())
                    .getContent();
            for (CaseFile caseFile : cases) {
                clearCaseFacts(caseFile.getId());
                for (CaseDocument document :
                        caseDocumentRepository.findByCaseFile_IdOrderByCreatedAtAsc(caseFile.getId())) {
                    document.setExtractedFieldsCipher(null);
                }
            }
            applicantRepository
                    .findByOrganizationIdAndKeycloakSubject(organizationId, subject)
                    .ifPresent(this::anonymizeApplicant);
            Instant now = Instant.now();
            request.setStatus(DataDeletionStatus.COMPLETED);
            request.setCompletedAt(now);
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("requestId", request.getId().toString());
            payload.put("completedAt", now.toString());
            outboxAppender.appendDataDeletionCompleted(request.getId(), payload);
            log.info("Retention purge completed for deletion request {}", request.getId());
        } finally {
            TenantContext.setOrganizationId(null);
        }
    }

    private void anonymizeApplicant(Applicant applicant) {
        applicant.setEmail(null);
        applicant.setDisplayName("anonymized");
        applicant.setFacts(new HashMap<>());
        applicant.setKeycloakSubject(null);
        applicantRepository.saveAndFlush(applicant);
    }

    private void clearCaseFacts(UUID caseId) {
        entityManager
                .createNativeQuery("UPDATE cases SET applicant_facts = '{}'::jsonb WHERE id = :id")
                .setParameter("id", caseId)
                .executeUpdate();
    }
}
