package com.konselyavisa.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.konselyavisa.common.exception.BusinessException;
import com.konselyavisa.document.api.DocumentUploadRefusalMapper;
import com.konselyavisa.document.api.DocumentUploadRefusalResponse;
import com.konselyavisa.document.domain.DocumentUploadRefusal;
import com.konselyavisa.document.persistence.DocumentUploadRefusalRepository;
import com.konselyavisa.dossier.CaseFile;
import com.konselyavisa.dossier.CaseFileRepository;
import com.konselyavisa.dossier.CaseService;
import com.konselyavisa.tenancy.TenantContext;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

@ExtendWith(MockitoExtension.class)
class DocumentUploadRefusalServiceTest {

    @Mock
    private CaseService caseService;

    @Mock
    private CaseFileRepository caseFileRepository;

    @Mock
    private DocumentUploadRefusalRepository documentUploadRefusalRepository;

    @Mock
    private DocumentUploadRefusalMapper documentUploadRefusalMapper;

    @Mock
    private PlatformTransactionManager transactionManager;

    private DocumentUploadRefusalService service;

    @BeforeEach
    void setUp() {
        lenient().when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        service = new DocumentUploadRefusalService(
                caseService,
                caseFileRepository,
                documentUploadRefusalRepository,
                documentUploadRefusalMapper,
                transactionManager);
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    void listForCaseRejectsCitizenScope() {
        authenticateAs("citizen-1", "citizen.dev", "ROLE_CITIZEN");

        assertThatThrownBy(() -> service.listForCase(UUID.randomUUID()))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getMessageKey())
                .isEqualTo("error.access.denied");
        verify(caseService, never()).requireAccessible(any());
    }

    @Test
    void listForCaseRejectsCompanyWorkspace() {
        authenticateAs("company-1", "company.dev", "ROLE_COMPANY_USER");

        assertThatThrownBy(() -> service.listForCase(UUID.randomUUID()))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getMessageKey())
                .isEqualTo("error.access.denied");
    }

    @Test
    void listForCaseReturnsMappedRefusalsForAgent() {
        authenticateAs("agent-1", "agent.dev", "ROLE_AGENT");
        UUID caseId = UUID.randomUUID();
        DocumentUploadRefusal refusal = new DocumentUploadRefusal();
        DocumentUploadRefusalResponse mapped = org.mockito.Mockito.mock(DocumentUploadRefusalResponse.class);
        when(documentUploadRefusalRepository.findByCaseFile_IdOrderByCreatedAtDesc(caseId))
                .thenReturn(List.of(refusal));
        when(documentUploadRefusalMapper.toResponse(refusal)).thenReturn(mapped);

        assertThat(service.listForCase(caseId)).containsExactly(mapped);
        verify(caseService).requireAccessible(caseId);
    }

    @Test
    void recordPersistsRefusalInNewTransactionWithSha256() {
        authenticateAs("agent-1", "agent.dev", "ROLE_AGENT");
        UUID caseId = UUID.randomUUID();
        UUID orgId = UUID.randomUUID();
        CaseFile caseFile = new CaseFile();
        caseFile.setId(caseId);
        caseFile.setOrganizationId(orgId);
        CaseFile reference = new CaseFile();
        reference.setId(caseId);
        when(caseFileRepository.getReferenceById(caseId)).thenReturn(reference);
        byte[] bytes = "passport-bytes".getBytes(StandardCharsets.UTF_8);
        MockMultipartFile file = new MockMultipartFile("file", "pass.pdf", "application/pdf", bytes);

        service.record(caseFile, "PASSPORT", file, "error.document.blurry");

        ArgumentCaptor<DocumentUploadRefusal> captor = ArgumentCaptor.forClass(DocumentUploadRefusal.class);
        verify(documentUploadRefusalRepository).save(captor.capture());
        DocumentUploadRefusal saved = captor.getValue();
        assertThat(saved.getOrganizationId()).isEqualTo(orgId);
        assertThat(saved.getCaseFile()).isSameAs(reference);
        assertThat(saved.getRequirementCode()).isEqualTo("PASSPORT");
        assertThat(saved.getReasonKey()).isEqualTo("error.document.blurry");
        assertThat(saved.getContentType()).isEqualTo("application/pdf");
        assertThat(saved.getSizeBytes()).isEqualTo((long) bytes.length);
        assertThat(saved.getSha256()).isEqualTo(DocumentFiles.sha256Hex(bytes));
        assertThat(saved.getOriginalFilename()).isEqualTo("pass.pdf");
        assertThat(saved.getAttemptedBy()).isEqualTo("agent-1");
    }

    @Test
    void recordUsesDefaultReasonWhenReasonKeyBlank() {
        CaseFile caseFile = new CaseFile();
        caseFile.setId(UUID.randomUUID());
        caseFile.setOrganizationId(UUID.randomUUID());
        when(caseFileRepository.getReferenceById(caseFile.getId())).thenReturn(caseFile);

        service.record(caseFile, "PASSPORT", null, "   ");

        ArgumentCaptor<DocumentUploadRefusal> captor = ArgumentCaptor.forClass(DocumentUploadRefusal.class);
        verify(documentUploadRefusalRepository).save(captor.capture());
        assertThat(captor.getValue().getReasonKey()).isEqualTo("error.document.upload_refused");
        assertThat(captor.getValue().getSha256()).isNull();
        assertThat(captor.getValue().getSizeBytes()).isNull();
    }

    @Test
    void recordTruncatesLongRequirementAndReason() {
        CaseFile caseFile = new CaseFile();
        caseFile.setId(UUID.randomUUID());
        caseFile.setOrganizationId(UUID.randomUUID());
        when(caseFileRepository.getReferenceById(caseFile.getId())).thenReturn(caseFile);
        String longRequirement = "R".repeat(80);
        String longReason = "E".repeat(200);

        service.record(caseFile, longRequirement, null, longReason);

        ArgumentCaptor<DocumentUploadRefusal> captor = ArgumentCaptor.forClass(DocumentUploadRefusal.class);
        verify(documentUploadRefusalRepository).save(captor.capture());
        assertThat(captor.getValue().getRequirementCode()).hasSize(50);
        assertThat(captor.getValue().getReasonKey()).hasSize(120);
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
