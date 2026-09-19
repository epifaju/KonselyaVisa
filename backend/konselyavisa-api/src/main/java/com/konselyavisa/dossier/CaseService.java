package com.konselyavisa.dossier;

import com.konselyavisa.applicant.Applicant;
import com.konselyavisa.applicant.ApplicantRepository;
import com.konselyavisa.catalog.domain.ProcedureDefinition;
import com.konselyavisa.catalog.domain.ProcedureVersion;
import com.konselyavisa.catalog.domain.ProcedureVersionStatus;
import com.konselyavisa.catalog.persistence.ProcedureDefinitionRepository;
import com.konselyavisa.catalog.persistence.ProcedureVersionRepository;
import com.konselyavisa.catalog.service.ProcedureAccessGuard;
import com.konselyavisa.common.api.PageResponse;
import com.konselyavisa.common.exception.BusinessException;
import com.konselyavisa.dossier.api.ApplicantRequest;
import com.konselyavisa.dossier.api.CaseResponse;
import com.konselyavisa.dossier.api.CreateCaseRequest;
import com.konselyavisa.eligibility.JsonLogicEligibilityEvaluator;
import com.konselyavisa.guest.GuestEligibilityService;
import com.konselyavisa.guest.GuestEligibilityTicket;
import com.konselyavisa.identity.CaseCreatorSnapshot;
import com.konselyavisa.identity.CurrentUser;
import com.konselyavisa.outbox.OutboxAppender;
import com.konselyavisa.privacy.PrivacyConsentService;
import com.konselyavisa.tenancy.TenantContext;
import java.time.Year;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CaseService {

    private final CaseFileRepository caseFileRepository;
    private final ApplicantRepository applicantRepository;
    private final ProcedureDefinitionRepository procedureDefinitionRepository;
    private final ProcedureVersionRepository procedureVersionRepository;
    private final JsonLogicEligibilityEvaluator eligibilityEvaluator;
    private final OutboxAppender outboxAppender;
    private final CaseResponseAssembler caseResponseAssembler;
    private final PrivacyConsentService privacyConsentService;
    private final ProcedureAccessGuard procedureAccessGuard;
    private final GuestEligibilityService guestEligibilityService;

    public CaseService(
            CaseFileRepository caseFileRepository,
            ApplicantRepository applicantRepository,
            ProcedureDefinitionRepository procedureDefinitionRepository,
            ProcedureVersionRepository procedureVersionRepository,
            JsonLogicEligibilityEvaluator eligibilityEvaluator,
            OutboxAppender outboxAppender,
            CaseResponseAssembler caseResponseAssembler,
            PrivacyConsentService privacyConsentService,
            ProcedureAccessGuard procedureAccessGuard,
            GuestEligibilityService guestEligibilityService) {
        this.caseFileRepository = caseFileRepository;
        this.applicantRepository = applicantRepository;
        this.procedureDefinitionRepository = procedureDefinitionRepository;
        this.procedureVersionRepository = procedureVersionRepository;
        this.eligibilityEvaluator = eligibilityEvaluator;
        this.outboxAppender = outboxAppender;
        this.caseResponseAssembler = caseResponseAssembler;
        this.privacyConsentService = privacyConsentService;
        this.procedureAccessGuard = procedureAccessGuard;
        this.guestEligibilityService = guestEligibilityService;
    }

    @Transactional
    public CaseResponse create(CreateCaseRequest request) {
        UUID organizationId = requireOrganization();
        UUID procedureId = request.procedureDefinitionId();
        Map<String, Object> facts = ApplicantFactsGuard.sanitize(
                request.applicant() == null ? Map.of() : request.applicant().facts());
        if (request.eligibilityTicketId() != null) {
            GuestEligibilityTicket ticket =
                    guestEligibilityService.consume(request.eligibilityTicketId(), organizationId);
            if (!ticket.procedureDefinitionId().equals(procedureId)) {
                throw BusinessException.badRequest("error.guest.ticket_mismatch");
            }
            facts = ApplicantFactsGuard.sanitize(ticket.facts());
        }
        ProcedureDefinition definition = procedureDefinitionRepository
                .findById(procedureId)
                .orElseThrow(() -> BusinessException.notFound("error.catalog.procedure_not_found"));
        if (!definition.isActive()) {
            throw BusinessException.badRequest("error.catalog.procedure_not_found");
        }
        procedureAccessGuard.assertOfferedToCitizens(definition);
        ProcedureVersion frozenVersion = procedureVersionRepository
                .findByProcedureDefinitionIdAndStatus(definition.getId(), ProcedureVersionStatus.PUBLISHED)
                .orElseThrow(() -> BusinessException.badRequest("error.case.procedure_not_published"));

        boolean eligible = eligibilityEvaluator.evaluate(frozenVersion.getEligibilityRules(), facts);
        if (!eligible) {
            throw BusinessException.badRequest("error.eligibility.not_met");
        }

        Applicant applicant = resolveApplicant(organizationId, request.applicant(), facts);
        CaseFile caseFile = new CaseFile();
        caseFile.setApplicant(applicant);
        caseFile.setProcedureDefinition(definition);
        caseFile.setProcedureVersion(frozenVersion);
        caseFile.setReference(nextReference(organizationId));
        caseFile.setStatus(CaseStatus.CREATED);
        caseFile.setApplicantFacts(new HashMap<>(facts));
        caseFile.setEligibilityPassed(true);
        caseFile.setCreatedByRole(CaseCreatorSnapshot.role());
        caseFile.setCreatedByLabel(CaseCreatorSnapshot.label());
        caseFileRepository.saveAndFlush(caseFile);
        privacyConsentService.recordCaseDeposit(caseFile, applicant, request.privacyConsent());
        outboxAppender.appendCaseCreated(caseFile.getId(), caseCreatedPayload(caseFile, frozenVersion));
        return caseResponseAssembler.toResponse(caseFile);
    }

    @Transactional(readOnly = true)
    public CaseResponse getById(UUID id) {
        return caseResponseAssembler.toResponse(requireAccessible(id));
    }

    @Transactional(readOnly = true)
    public CaseFile requireAccessible(UUID id) {
        CaseFile caseFile = caseFileRepository
                .findDetailedById(id)
                .orElseThrow(() -> BusinessException.notFound("error.case.not_found"));
        assertCanView(caseFile);
        return caseFile;
    }

    @Transactional(readOnly = true)
    public PageResponse<CaseResponse> list(Pageable pageable) {
        Page<CaseFile> page;
        if (CurrentUser.isCitizenScoped()) {
            String subject = CurrentUser.subject();
            if (subject == null) {
                throw BusinessException.forbidden("error.access.denied");
            }
            page = caseFileRepository.findAllByApplicant_KeycloakSubject(subject, pageable);
        } else if (CurrentUser.isCompanyCollaborator()) {
            String username = CurrentUser.username();
            if (username == null) {
                throw BusinessException.forbidden("error.access.denied");
            }
            page = caseFileRepository.findAllByCreatedBy(username, pageable);
        } else if (CurrentUser.isCompanyOrgAdmin()) {
            page = caseFileRepository.findAllByCreatedByRoleIn(List.of("COMPANY_USER", "COMPANY_ADMIN"), pageable);
        } else {
            page = caseFileRepository.findAll(pageable);
        }
        List<CaseResponse> content = new ArrayList<>(caseResponseAssembler.toResponses(page.getContent()));
        content.sort(CaseListUrgency.LIST_ORDER);
        return new PageResponse<>(
                content,
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isFirst(),
                page.isLast());
    }

    private Applicant resolveApplicant(UUID organizationId, ApplicantRequest request, Map<String, Object> facts) {
        String subject = CurrentUser.isCitizenScoped() ? CurrentUser.subject() : null;
        if (subject != null) {
            return applicantRepository
                    .findByOrganizationIdAndKeycloakSubject(organizationId, subject)
                    .map(existing -> {
                        existing.setFacts(new HashMap<>(facts));
                        if (request != null && request.email() != null) {
                            existing.setEmail(request.email());
                        }
                        if (request != null && request.displayName() != null) {
                            existing.setDisplayName(request.displayName());
                        }
                        return existing;
                    })
                    .orElseGet(() -> persistApplicant(subject, request, facts));
        }
        return persistApplicant(null, request, facts);
    }

    private Applicant persistApplicant(String subject, ApplicantRequest request, Map<String, Object> facts) {
        Applicant applicant = new Applicant();
        applicant.setKeycloakSubject(subject);
        if (request != null) {
            applicant.setEmail(request.email());
            applicant.setDisplayName(request.displayName());
        }
        applicant.setFacts(new HashMap<>(facts));
        return applicantRepository.save(applicant);
    }

    private String nextReference(UUID organizationId) {
        for (int attempt = 0; attempt < 8; attempt++) {
            String reference = "KV-" + Year.now() + "-"
                    + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
            if (!caseFileRepository.existsByOrganizationIdAndReference(organizationId, reference)) {
                return reference;
            }
        }
        throw BusinessException.conflict("error.case.reference_collision");
    }

    private static Map<String, Object> caseCreatedPayload(CaseFile caseFile, ProcedureVersion version) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("caseId", caseFile.getId().toString());
        payload.put("reference", caseFile.getReference());
        payload.put("organizationId", caseFile.getOrganizationId().toString());
        payload.put("procedureDefinitionId", caseFile.getProcedureDefinition().getId().toString());
        payload.put("procedureVersionId", version.getId().toString());
        payload.put("procedureVersionNumber", version.getVersionNumber());
        return payload;
    }

    private static UUID requireOrganization() {
        UUID organizationId = TenantContext.getOrganizationId();
        if (organizationId == null) {
            throw BusinessException.forbidden("error.organization.missing");
        }
        return organizationId;
    }

    private static void assertCanView(CaseFile caseFile) {
        if (CurrentUser.isCitizenScoped()) {
            String subject = CurrentUser.subject();
            if (subject == null
                    || caseFile.getApplicant() == null
                    || !subject.equals(caseFile.getApplicant().getKeycloakSubject())) {
                throw BusinessException.forbidden("error.access.denied");
            }
            return;
        }
        if (CurrentUser.isCompanyCollaborator()) {
            String username = CurrentUser.username();
            if (username == null || !username.equals(caseFile.getCreatedBy())) {
                throw BusinessException.forbidden("error.access.denied");
            }
            return;
        }
        if (CurrentUser.isCompanyOrgAdmin()) {
            if (!"COMPANY_USER".equals(caseFile.getCreatedByRole())
                    && !"COMPANY_ADMIN".equals(caseFile.getCreatedByRole())) {
                throw BusinessException.forbidden("error.access.denied");
            }
        }
    }
}
