package com.konselyavisa.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.konselyavisa.applicant.Applicant;
import com.konselyavisa.common.exception.BusinessException;
import com.konselyavisa.document.api.DocumentHashAlertResponse;
import com.konselyavisa.document.domain.CaseDocument;
import com.konselyavisa.document.persistence.CaseDocumentRepository;
import com.konselyavisa.dossier.CaseFile;
import com.konselyavisa.dossier.CaseService;
import com.konselyavisa.tenancy.TenantContext;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

@ExtendWith(MockitoExtension.class)
class DocumentHashAlertServiceTest {

    @Mock
    private CaseService caseService;

    @Mock
    private CaseDocumentRepository caseDocumentRepository;

    private DocumentHashAlertService service;

    @BeforeEach
    void setUp() {
        service = new DocumentHashAlertService(caseService, caseDocumentRepository);
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    void flagCrossApplicantDuplicatesNoOpForSingleApplicant() {
        UUID orgId = UUID.randomUUID();
        Applicant applicant = applicant("Ada");
        CaseDocument document = document(orgId, "hash-1", "PASSPORT", caseFile(applicant, "KV-1"));
        when(caseDocumentRepository.findByOrganizationIdAndSha256In(orgId, List.of("hash-1")))
                .thenReturn(List.of(document, document(orgId, "hash-1", "ID", caseFile(applicant, "KV-2"))));

        service.flagCrossApplicantDuplicates(document);

        assertThat(document.isDuplicateHash()).isFalse();
    }

    @Test
    void flagCrossApplicantDuplicatesSetsFlagWhenMultipleApplicants() {
        UUID orgId = UUID.randomUUID();
        CaseDocument first = document(orgId, "hash-1", "PASSPORT", caseFile(applicant("Ada"), "KV-1"));
        CaseDocument second = document(orgId, "hash-1", "PASSPORT", caseFile(applicant("Bob"), "KV-2"));
        when(caseDocumentRepository.findByOrganizationIdAndSha256In(orgId, List.of("hash-1")))
                .thenReturn(List.of(first, second));

        service.flagCrossApplicantDuplicates(first);

        assertThat(first.isDuplicateHash()).isTrue();
        assertThat(second.isDuplicateHash()).isTrue();
    }

    @Test
    void listForCaseForbiddenForCitizen() {
        authenticateAs("citizen-1", "citizen.dev", "ROLE_CITIZEN");

        assertThatThrownBy(() -> service.listForCase(UUID.randomUUID()))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getMessageKey())
                .isEqualTo("error.access.denied");
        verify(caseService, never()).requireAccessible(any());
    }

    @Test
    void listForCaseReturnsEmptyWhenNoDocuments() {
        authenticateAs("agent-1", "agent.dev", "ROLE_AGENT");
        UUID caseId = UUID.randomUUID();
        CaseFile caseFile = caseFile(applicant("Ada"), "KV-1");
        when(caseService.requireAccessible(caseId)).thenReturn(caseFile);
        when(caseDocumentRepository.findByCaseFile_IdOrderByCreatedAtAsc(caseId)).thenReturn(List.of());

        assertThat(service.listForCase(caseId)).isEmpty();
        verify(caseDocumentRepository, never()).findByOrganizationIdAndSha256In(any(), anyList());
    }

    @Test
    void listForCaseBuildsAlertsForCrossApplicantHash() {
        authenticateAs("agent-1", "agent.dev", "ROLE_AGENT");
        UUID orgId = UUID.randomUUID();
        UUID caseId = UUID.randomUUID();
        Applicant owner = applicant("Ada");
        CaseFile caseFile = caseFile(owner, "KV-1");
        caseFile.setId(caseId);
        caseFile.setOrganizationId(orgId);
        CaseDocument owned = document(orgId, "hash-1", "PASSPORT", caseFile);
        CaseDocument other = document(orgId, "hash-1", "ID_CARD", caseFile(applicant(null), "KV-OTHER"));
        when(caseService.requireAccessible(caseId)).thenReturn(caseFile);
        when(caseDocumentRepository.findByCaseFile_IdOrderByCreatedAtAsc(caseId)).thenReturn(List.of(owned));
        when(caseDocumentRepository.findByOrganizationIdAndSha256In(eq(orgId), eq(List.of("hash-1"))))
                .thenReturn(List.of(owned, other));

        List<DocumentHashAlertResponse> alerts = service.listForCase(caseId);

        assertThat(alerts).hasSize(1);
        assertThat(alerts.get(0).documentId()).isEqualTo(owned.getId());
        assertThat(alerts.get(0).requirementCode()).isEqualTo("PASSPORT");
        assertThat(alerts.get(0).matches()).hasSize(1);
        assertThat(alerts.get(0).matches().get(0).caseReference()).isEqualTo("KV-OTHER");
        assertThat(alerts.get(0).matches().get(0).applicantDisplayName()).isEqualTo("—");
        assertThat(alerts.get(0).matches().get(0).requirementCode()).isEqualTo("ID_CARD");
    }

    private static Applicant applicant(String displayName) {
        Applicant applicant = new Applicant();
        applicant.setId(UUID.randomUUID());
        applicant.setDisplayName(displayName);
        return applicant;
    }

    private static CaseFile caseFile(Applicant applicant, String reference) {
        CaseFile caseFile = new CaseFile();
        caseFile.setId(UUID.randomUUID());
        caseFile.setApplicant(applicant);
        caseFile.setReference(reference);
        return caseFile;
    }

    private static CaseDocument document(UUID orgId, String sha256, String requirement, CaseFile caseFile) {
        CaseDocument document = new CaseDocument();
        document.setId(UUID.randomUUID());
        document.setOrganizationId(orgId);
        document.setSha256(sha256);
        document.setRequirementCode(requirement);
        document.setCaseFile(caseFile);
        return document;
    }

    private static void authenticateAs(String subject, String username, String... roles) {
        Jwt jwt = Jwt.withTokenValue("test")
                .header("alg", "none")
                .subject(subject)
                .claim("preferred_username", username)
                .build();
        SecurityContextHolder.getContext()
                .setAuthentication(new JwtAuthenticationToken(
                        jwt, java.util.Arrays.stream(roles).map(SimpleGrantedAuthority::new).toList()));
    }
}
