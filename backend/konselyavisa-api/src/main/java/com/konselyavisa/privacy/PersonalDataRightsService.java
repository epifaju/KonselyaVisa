package com.konselyavisa.privacy;

import com.konselyavisa.applicant.Applicant;
import com.konselyavisa.applicant.ApplicantRepository;
import com.konselyavisa.common.exception.BusinessException;
import com.konselyavisa.document.domain.CaseDocument;
import com.konselyavisa.document.persistence.CaseDocumentRepository;
import com.konselyavisa.dossier.CaseFile;
import com.konselyavisa.dossier.CaseFileRepository;
import com.konselyavisa.identity.CurrentUser;
import com.konselyavisa.outbox.OutboxAppender;
import com.konselyavisa.privacy.api.DataDeletionRequestResponse;
import com.konselyavisa.privacy.api.PersonalDataExportResponse;
import com.konselyavisa.privacy.api.PersonalDataExportResponse.ApplicantExport;
import com.konselyavisa.privacy.api.PersonalDataExportResponse.CaseExport;
import com.konselyavisa.privacy.api.PersonalDataExportResponse.ConsentExport;
import com.konselyavisa.privacy.api.PersonalDataExportResponse.DeletionExport;
import com.konselyavisa.privacy.api.PersonalDataExportResponse.DocumentExport;
import com.konselyavisa.privacy.domain.DataDeletionRequest;
import com.konselyavisa.privacy.domain.DataDeletionStatus;
import com.konselyavisa.privacy.domain.PrivacyConsent;
import com.konselyavisa.privacy.persistence.DataDeletionRequestRepository;
import com.konselyavisa.privacy.persistence.PrivacyConsentRepository;
import com.konselyavisa.tenancy.TenantContext;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PersonalDataRightsService {

    private final ApplicantRepository applicantRepository;
    private final CaseFileRepository caseFileRepository;
    private final CaseDocumentRepository caseDocumentRepository;
    private final PrivacyConsentRepository privacyConsentRepository;
    private final DataDeletionRequestRepository dataDeletionRequestRepository;
    private final OutboxAppender outboxAppender;
    private final KonselyaPrivacyProperties properties;

    public PersonalDataRightsService(
            ApplicantRepository applicantRepository,
            CaseFileRepository caseFileRepository,
            CaseDocumentRepository caseDocumentRepository,
            PrivacyConsentRepository privacyConsentRepository,
            DataDeletionRequestRepository dataDeletionRequestRepository,
            OutboxAppender outboxAppender,
            KonselyaPrivacyProperties properties) {
        this.applicantRepository = applicantRepository;
        this.caseFileRepository = caseFileRepository;
        this.caseDocumentRepository = caseDocumentRepository;
        this.privacyConsentRepository = privacyConsentRepository;
        this.dataDeletionRequestRepository = dataDeletionRequestRepository;
        this.outboxAppender = outboxAppender;
        this.properties = properties;
    }

    @Transactional(readOnly = true)
    public PersonalDataExportResponse exportMine() {
        String subject = requireSubject();
        UUID organizationId = requireOrganization();
        Applicant applicant = applicantRepository
                .findByOrganizationIdAndKeycloakSubject(organizationId, subject)
                .orElse(null);
        List<CaseFile> cases = caseFileRepository
                .findAllByApplicant_KeycloakSubject(subject, Pageable.unpaged())
                .getContent();
        List<UUID> caseIds = cases.stream().map(CaseFile::getId).toList();
        Map<UUID, List<CaseDocument>> documentsByCase = new LinkedHashMap<>();
        if (!caseIds.isEmpty()) {
            for (CaseDocument document : caseDocumentRepository.findByCaseFile_IdIn(caseIds)) {
                documentsByCase
                        .computeIfAbsent(document.getCaseFile().getId(), ignored -> new ArrayList<>())
                        .add(document);
            }
        }
        List<CaseExport> caseExports = cases.stream()
                .map(caseFile -> new CaseExport(
                        caseFile.getReference(),
                        caseFile.getStatus().name(),
                        caseFile.getProcedureDefinition().getCode(),
                        caseFile.getCreatedAt(),
                        caseFile.getUpdatedAt(),
                        documentsByCase.getOrDefault(caseFile.getId(), List.of()).stream()
                                .map(document -> new DocumentExport(
                                        document.getRequirementCode(),
                                        document.getStatus().name(),
                                        document.getCreatedAt()))
                                .toList()))
                .toList();
        List<ConsentExport> consents = privacyConsentRepository
                .findByKeycloakSubjectOrderByAcceptedAtDesc(subject)
                .stream()
                .map(this::toConsentExport)
                .toList();
        List<DeletionExport> deletions = dataDeletionRequestRepository
                .findByKeycloakSubjectOrderByRequestedAtDesc(subject)
                .stream()
                .map(item -> new DeletionExport(
                        item.getStatus().name(), item.getRequestedAt(), item.getScheduledAnonymizeAt()))
                .toList();
        ApplicantExport applicantExport = applicant == null
                ? new ApplicantExport(null, null, Map.of())
                : new ApplicantExport(applicant.getEmail(), applicant.getDisplayName(), applicant.getFacts());
        return new PersonalDataExportResponse(
                Instant.now(), subject, applicantExport, consents, caseExports, deletions);
    }

    @Transactional
    public DataDeletionRequestResponse requestDeletion() {
        String subject = requireSubject();
        requireOrganization();
        return dataDeletionRequestRepository
                .findFirstByKeycloakSubjectAndStatusOrderByRequestedAtDesc(subject, DataDeletionStatus.PENDING)
                .map(this::toDeletionResponse)
                .orElseGet(() -> createDeletionRequest(subject));
    }

    private DataDeletionRequestResponse createDeletionRequest(String subject) {
        Duration delay = properties.getAnonymizeAfter() == null || properties.getAnonymizeAfter().isNegative()
                ? Duration.ofDays(1825)
                : properties.getAnonymizeAfter();
        Instant now = Instant.now();
        DataDeletionRequest request = new DataDeletionRequest();
        request.setKeycloakSubject(subject);
        request.setStatus(DataDeletionStatus.PENDING);
        request.setRequestedAt(now);
        request.setScheduledAnonymizeAt(now.plus(delay));
        dataDeletionRequestRepository.saveAndFlush(request);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("requestId", request.getId().toString());
        payload.put("scheduledAnonymizeAt", request.getScheduledAnonymizeAt().toString());
        outboxAppender.appendDataDeletionRequested(request.getId(), payload);
        return toDeletionResponse(request);
    }

    private ConsentExport toConsentExport(PrivacyConsent consent) {
        return new ConsentExport(
                consent.getPurpose().name(),
                consent.getNoticeVersion(),
                consent.getLocale(),
                consent.getAcceptedAt());
    }

    private DataDeletionRequestResponse toDeletionResponse(DataDeletionRequest request) {
        return new DataDeletionRequestResponse(
                request.getId(),
                request.getStatus(),
                request.getRequestedAt(),
                request.getScheduledAnonymizeAt(),
                request.getCompletedAt());
    }

    private static String requireSubject() {
        String subject = CurrentUser.subject();
        if (subject == null) {
            throw BusinessException.forbidden("error.access.denied");
        }
        return subject;
    }

    private static UUID requireOrganization() {
        UUID organizationId = TenantContext.getOrganizationId();
        if (organizationId == null) {
            throw BusinessException.forbidden("error.organization.missing");
        }
        return organizationId;
    }
}
