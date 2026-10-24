package com.konselyavisa.dossier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.konselyavisa.applicant.Applicant;
import com.konselyavisa.applicant.ApplicantRepository;
import com.konselyavisa.catalog.domain.ProcedureDefinition;
import com.konselyavisa.catalog.domain.ProcedureVersion;
import com.konselyavisa.catalog.domain.ProcedureVersionStatus;
import com.konselyavisa.catalog.persistence.ProcedureDefinitionRepository;
import com.konselyavisa.catalog.persistence.ProcedureVersionRepository;
import com.konselyavisa.catalog.service.ProcedureAccessGuard;
import com.konselyavisa.common.exception.BusinessException;
import com.konselyavisa.dossier.api.ApplicantRequest;
import com.konselyavisa.dossier.api.CaseResponse;
import com.konselyavisa.dossier.api.CreateCaseRequest;
import com.konselyavisa.dossier.api.PrivacyConsentAcceptance;
import com.konselyavisa.eligibility.JsonLogicEligibilityEvaluator;
import com.konselyavisa.guest.GuestEligibilityService;
import com.konselyavisa.outbox.OutboxAppender;
import com.konselyavisa.privacy.PrivacyConsentService;
import com.konselyavisa.common.api.PageResponse;
import com.konselyavisa.tenancy.TenantContext;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

@ExtendWith(MockitoExtension.class)
class CaseServiceTest {

    @Mock
    private CaseFileRepository caseFileRepository;

    @Mock
    private ApplicantRepository applicantRepository;

    @Mock
    private ProcedureDefinitionRepository procedureDefinitionRepository;

    @Mock
    private ProcedureVersionRepository procedureVersionRepository;

    @Mock
    private JsonLogicEligibilityEvaluator eligibilityEvaluator;

    @Mock
    private OutboxAppender outboxAppender;

    @Mock
    private CaseResponseAssembler caseResponseAssembler;

    @Mock
    private PrivacyConsentService privacyConsentService;

    @Mock
    private ProcedureAccessGuard procedureAccessGuard;

    @Mock
    private GuestEligibilityService guestEligibilityService;

    @InjectMocks
    private CaseService caseService;

    private UUID orgId;
    private UUID procedureId;

    @BeforeEach
    void setUp() {
        orgId = UUID.randomUUID();
        procedureId = UUID.randomUUID();
        TenantContext.clear();
        authenticateAsCitizen();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    void createRequiresOrganization() {
        assertThatThrownBy(() -> caseService.create(request(Map.of("age", 30))))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getMessageKey())
                .isEqualTo("error.organization.missing");
    }

    @Test
    void createFailsWhenProcedureMissing() {
        TenantContext.setOrganizationId(orgId);
        when(procedureDefinitionRepository.findById(procedureId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> caseService.create(request(Map.of("age", 30))))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getMessageKey())
                .isEqualTo("error.catalog.procedure_not_found");
    }

    @Test
    void createFailsWhenEligibilityNotMet() {
        TenantContext.setOrganizationId(orgId);
        ProcedureDefinition definition = publishedDefinition();
        ProcedureVersion version = publishedVersion(definition);
        when(procedureDefinitionRepository.findById(procedureId)).thenReturn(Optional.of(definition));
        when(procedureVersionRepository.findByProcedureDefinitionIdAndStatus(
                        definition.getId(), ProcedureVersionStatus.PUBLISHED))
                .thenReturn(Optional.of(version));
        when(eligibilityEvaluator.evaluate(eq(version.getEligibilityRules()), any())).thenReturn(false);

        assertThatThrownBy(() -> caseService.create(request(Map.of("age", 16))))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getMessageKey())
                .isEqualTo("error.eligibility.not_met");
        verify(caseFileRepository, never()).saveAndFlush(any());
    }

    @Test
    void createPersistsCaseAndEmitsOutbox() {
        TenantContext.setOrganizationId(orgId);
        ProcedureDefinition definition = publishedDefinition();
        ProcedureVersion version = publishedVersion(definition);
        Map<String, Object> facts = Map.of("age", 30);
        when(procedureDefinitionRepository.findById(procedureId)).thenReturn(Optional.of(definition));
        when(procedureVersionRepository.findByProcedureDefinitionIdAndStatus(
                        definition.getId(), ProcedureVersionStatus.PUBLISHED))
                .thenReturn(Optional.of(version));
        when(eligibilityEvaluator.evaluate(eq(version.getEligibilityRules()), any())).thenReturn(true);
        when(applicantRepository.findByOrganizationIdAndKeycloakSubject(orgId, "citizen-1"))
                .thenReturn(Optional.empty());
        when(applicantRepository.save(any(Applicant.class))).thenAnswer(inv -> {
            Applicant applicant = inv.getArgument(0);
            applicant.setId(UUID.randomUUID());
            return applicant;
        });
        when(caseFileRepository.existsByOrganizationIdAndReference(eq(orgId), any())).thenReturn(false);
        when(caseFileRepository.saveAndFlush(any(CaseFile.class))).thenAnswer(inv -> {
            CaseFile saved = inv.getArgument(0);
            saved.setId(UUID.randomUUID());
            saved.setOrganizationId(orgId);
            return saved;
        });
        when(caseResponseAssembler.toResponse(any(CaseFile.class))).thenAnswer(inv -> {
            CaseFile saved = inv.getArgument(0);
            return new CaseResponse(
                    saved.getId(),
                    saved.getReference(),
                    saved.getStatus(),
                    orgId,
                    null,
                    procedureId,
                    "VISA_TOURISM",
                    Map.of("fr", "Visa"),
                    null,
                    null,
                    version.getId(),
                    1,
                    null,
                    facts,
                    true,
                    Instant.now(),
                    Instant.now(),
                    null,
                    "CITIZEN",
                    "citizen.dev",
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    false);
        });

        CaseResponse response = caseService.create(request(facts));

        assertThat(response.status()).isEqualTo(CaseStatus.CREATED);
        assertThat(response.eligibilityPassed()).isTrue();
        ArgumentCaptor<CaseFile> caseCaptor = ArgumentCaptor.forClass(CaseFile.class);
        verify(caseFileRepository).saveAndFlush(caseCaptor.capture());
        assertThat(caseCaptor.getValue().getStatus()).isEqualTo(CaseStatus.CREATED);
        assertThat(caseCaptor.getValue().isEligibilityPassed()).isTrue();
        verify(privacyConsentService).recordCaseDeposit(any(CaseFile.class), any(Applicant.class), any());
        verify(outboxAppender).appendCaseCreated(eq(response.id()), any());
    }

    @Test
    void requireAccessibleRejectsUnknownCase() {
        UUID caseId = UUID.randomUUID();
        when(caseFileRepository.findDetailedById(caseId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> caseService.requireAccessible(caseId))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getMessageKey())
                .isEqualTo("error.case.not_found");
    }

    @Test
    void requireAccessibleDeniesCitizenForForeignApplicant() {
        UUID caseId = UUID.randomUUID();
        CaseFile caseFile = caseOwnedBy("other-citizen");
        when(caseFileRepository.findDetailedById(caseId)).thenReturn(Optional.of(caseFile));

        assertThatThrownBy(() -> caseService.requireAccessible(caseId))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getMessageKey())
                .isEqualTo("error.access.denied");
    }

    @Test
    void requireAccessibleAllowsCitizenOwner() {
        UUID caseId = UUID.randomUUID();
        CaseFile caseFile = caseOwnedBy("citizen-1");
        when(caseFileRepository.findDetailedById(caseId)).thenReturn(Optional.of(caseFile));

        assertThat(caseService.requireAccessible(caseId)).isSameAs(caseFile);
    }

    @Test
    void requireAccessibleAllowsAgentForAnyCase() {
        authenticateAs("agent-1", "agent.dev", "ROLE_AGENT");
        UUID caseId = UUID.randomUUID();
        CaseFile caseFile = caseOwnedBy("someone-else");
        when(caseFileRepository.findDetailedById(caseId)).thenReturn(Optional.of(caseFile));

        assertThat(caseService.requireAccessible(caseId)).isSameAs(caseFile);
    }

    @Test
    void requireAccessibleDeniesCompanyCollaboratorForOtherCreator() {
        authenticateAs("company-1", "company.dev", "ROLE_COMPANY_USER");
        UUID caseId = UUID.randomUUID();
        CaseFile caseFile = caseOwnedBy("citizen-x");
        caseFile.setCreatedBy("other.user");
        when(caseFileRepository.findDetailedById(caseId)).thenReturn(Optional.of(caseFile));

        assertThatThrownBy(() -> caseService.requireAccessible(caseId))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getMessageKey())
                .isEqualTo("error.access.denied");
    }

    @Test
    void requireAccessibleAllowsCompanyCollaboratorForOwnCreations() {
        authenticateAs("company-1", "company.dev", "ROLE_COMPANY_USER");
        UUID caseId = UUID.randomUUID();
        CaseFile caseFile = caseOwnedBy("citizen-x");
        caseFile.setCreatedBy("company.dev");
        when(caseFileRepository.findDetailedById(caseId)).thenReturn(Optional.of(caseFile));

        assertThat(caseService.requireAccessible(caseId)).isSameAs(caseFile);
    }

    @Test
    void requireAccessibleAllowsCompanyOrgAdminForCompanyCases() {
        authenticateAs("admin-1", "org.admin", "ROLE_COMPANY_USER", "ROLE_BUSINESS_ADMIN");
        UUID caseId = UUID.randomUUID();
        CaseFile caseFile = caseOwnedBy("citizen-x");
        caseFile.setCreatedBy("collaborator.dev");
        caseFile.setCreatedByRole("COMPANY_USER");
        when(caseFileRepository.findDetailedById(caseId)).thenReturn(Optional.of(caseFile));

        assertThat(caseService.requireAccessible(caseId)).isSameAs(caseFile);
    }

    @Test
    void requireAccessibleDeniesCompanyOrgAdminForCitizenCases() {
        authenticateAs("admin-1", "org.admin", "ROLE_COMPANY_USER", "ROLE_BUSINESS_ADMIN");
        UUID caseId = UUID.randomUUID();
        CaseFile caseFile = caseOwnedBy("citizen-x");
        caseFile.setCreatedByRole("CITIZEN");
        when(caseFileRepository.findDetailedById(caseId)).thenReturn(Optional.of(caseFile));

        assertThatThrownBy(() -> caseService.requireAccessible(caseId))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getMessageKey())
                .isEqualTo("error.access.denied");
    }

    @Test
    void listScopesToCitizenSubject() {
        Pageable pageable = PageRequest.of(0, 20);
        CaseFile caseFile = caseOwnedBy("citizen-1");
        when(caseFileRepository.findAllByApplicant_KeycloakSubject("citizen-1", pageable))
                .thenReturn(new PageImpl<>(List.of(caseFile), pageable, 1));
        CaseResponse mapped = minimalResponse(caseFile.getId(), caseFile.getReference());
        when(caseResponseAssembler.toResponses(List.of(caseFile))).thenReturn(List.of(mapped));

        PageResponse<CaseResponse> page = caseService.list(pageable);

        assertThat(page.content()).containsExactly(mapped);
        assertThat(page.totalElements()).isEqualTo(1);
        verify(caseFileRepository).findAllByApplicant_KeycloakSubject("citizen-1", pageable);
        verify(caseFileRepository, never()).findAll(any(Pageable.class));
    }

    @Test
    void listScopesToCompanyCollaboratorCreator() {
        authenticateAs("company-1", "company.dev", "ROLE_COMPANY_USER");
        Pageable pageable = PageRequest.of(0, 10);
        when(caseFileRepository.findAllByCreatedBy("company.dev", pageable)).thenReturn(Page.empty(pageable));
        when(caseResponseAssembler.toResponses(List.of())).thenReturn(List.of());

        PageResponse<CaseResponse> page = caseService.list(pageable);

        assertThat(page.content()).isEmpty();
        verify(caseFileRepository).findAllByCreatedBy("company.dev", pageable);
    }

    @Test
    void listUsesStaffQueueForAgents() {
        authenticateAs("agent-1", "agent.dev", "ROLE_AGENT");
        Pageable pageable = PageRequest.of(0, 10);
        when(caseFileRepository.findAll(pageable)).thenReturn(Page.empty(pageable));
        when(caseResponseAssembler.toResponses(List.of())).thenReturn(List.of());

        caseService.list(pageable);

        verify(caseFileRepository).findAll(pageable);
    }

    @Test
    void listScopesToCompanyOrgAdminRoles() {
        authenticateAs("admin-1", "org.admin", "ROLE_COMPANY_USER", "ROLE_BUSINESS_ADMIN");
        Pageable pageable = PageRequest.of(0, 10);
        when(caseFileRepository.findAllByCreatedByRoleIn(
                        List.of("COMPANY_USER", "COMPANY_ADMIN"), pageable))
                .thenReturn(Page.empty(pageable));
        when(caseResponseAssembler.toResponses(List.of())).thenReturn(List.of());

        caseService.list(pageable);

        verify(caseFileRepository)
                .findAllByCreatedByRoleIn(List.of("COMPANY_USER", "COMPANY_ADMIN"), pageable);
        verify(caseFileRepository, never()).findAllByCreatedBy(any(), any());
    }

    @Test
    void getByIdDelegatesToAssembler() {
        UUID caseId = UUID.randomUUID();
        CaseFile caseFile = caseOwnedBy("citizen-1");
        when(caseFileRepository.findDetailedById(caseId)).thenReturn(Optional.of(caseFile));
        CaseResponse mapped = minimalResponse(caseId, "KV-1");
        when(caseResponseAssembler.toResponse(caseFile)).thenReturn(mapped);

        assertThat(caseService.getById(caseId)).isSameAs(mapped);
    }

    private CaseFile caseOwnedBy(String subject) {
        Applicant applicant = new Applicant();
        applicant.setId(UUID.randomUUID());
        applicant.setKeycloakSubject(subject);
        CaseFile caseFile = new CaseFile();
        caseFile.setId(UUID.randomUUID());
        caseFile.setReference("KV-2026-CASE0001");
        caseFile.setStatus(CaseStatus.CREATED);
        caseFile.setApplicant(applicant);
        caseFile.setCreatedBy("creator");
        caseFile.setCreatedByRole("CITIZEN");
        return caseFile;
    }

    private CaseResponse minimalResponse(UUID id, String reference) {
        return new CaseResponse(
                id,
                reference,
                CaseStatus.CREATED,
                orgId,
                null,
                procedureId,
                "VISA_TOURISM",
                Map.of("fr", "Visa"),
                null,
                null,
                UUID.randomUUID(),
                1,
                null,
                Map.of(),
                true,
                Instant.now(),
                Instant.now(),
                null,
                "CITIZEN",
                "citizen.dev",
                null,
                null,
                null,
                null,
                null,
                null,
                false);
    }

    private CreateCaseRequest request(Map<String, Object> facts) {
        return new CreateCaseRequest(
                procedureId,
                new ApplicantRequest("citizen@example.com", "Citizen Dev", facts),
                PrivacyConsentAcceptance.currentAccepted("fr"));
    }

    private ProcedureDefinition publishedDefinition() {
        ProcedureDefinition definition = new ProcedureDefinition();
        definition.setId(procedureId);
        definition.setCode("VISA_TOURISM");
        definition.setActive(true);
        definition.setNameI18n(Map.of("fr", "Visa"));
        return definition;
    }

    private ProcedureVersion publishedVersion(ProcedureDefinition definition) {
        ProcedureVersion version = new ProcedureVersion();
        version.setId(UUID.randomUUID());
        version.setProcedureDefinition(definition);
        version.setStatus(ProcedureVersionStatus.PUBLISHED);
        version.setVersionNumber(1);
        version.setEligibilityRules(Map.of("==", List.of(Map.of("var", "age"), 30)));
        version.setDocumentRequirements(List.of());
        version.setPricing(Map.of("currency", "EUR", "amountMinor", 8500));
        return version;
    }

    private static void authenticateAsCitizen() {
        authenticateAs("citizen-1", "citizen.dev", "ROLE_CITIZEN");
    }

    private static void authenticateAs(String subject, String username, String... roles) {
        Jwt jwt = Jwt.withTokenValue("test")
                .header("alg", "none")
                .subject(subject)
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .claim("preferred_username", username)
                .build();
        SecurityContextHolder.getContext()
                .setAuthentication(new JwtAuthenticationToken(
                        jwt,
                        java.util.Arrays.stream(roles).map(SimpleGrantedAuthority::new).toList()));
    }
}
