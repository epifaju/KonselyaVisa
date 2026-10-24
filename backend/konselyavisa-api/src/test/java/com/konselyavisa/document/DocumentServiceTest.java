package com.konselyavisa.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.konselyavisa.applicant.Applicant;
import com.konselyavisa.catalog.domain.ProcedureVersion;
import com.konselyavisa.common.exception.BusinessException;
import com.konselyavisa.document.api.DocumentResponse;
import com.konselyavisa.document.domain.CaseDocument;
import com.konselyavisa.document.domain.DocumentAccessAction;
import com.konselyavisa.document.domain.DocumentAccessLog;
import com.konselyavisa.document.domain.DocumentStatus;
import com.konselyavisa.document.persistence.CaseDocumentRepository;
import com.konselyavisa.document.persistence.DocumentAccessLogRepository;
import com.konselyavisa.document.storage.ObjectStorage;
import com.konselyavisa.dossier.CaseFile;
import com.konselyavisa.dossier.CaseService;
import com.konselyavisa.dossier.CaseStatus;
import com.konselyavisa.outbox.OutboxAppender;
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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

@ExtendWith(MockitoExtension.class)
class DocumentServiceTest {

    @Mock
    private CaseService caseService;

    @Mock
    private CaseDocumentRepository caseDocumentRepository;

    @Mock
    private DocumentAccessLogRepository documentAccessLogRepository;

    @Mock
    private ObjectStorage objectStorage;

    @Mock
    private OutboxAppender outboxAppender;

    @Mock
    private DocumentResponseFactory documentResponseFactory;

    @Mock
    private DocumentHashAlertService documentHashAlertService;

    @Mock
    private DocumentUploadRefusalService documentUploadRefusalService;

    private DocumentService service;
    private UUID caseId;
    private UUID orgId;
    private CaseFile caseFile;

    @BeforeEach
    void setUp() {
        caseId = UUID.randomUUID();
        orgId = UUID.randomUUID();
        caseFile = openCase();
        service = new DocumentService(
                caseService,
                caseDocumentRepository,
                documentAccessLogRepository,
                objectStorage,
                outboxAppender,
                documentResponseFactory,
                documentHashAlertService,
                documentUploadRefusalService);
        authenticateAsCitizen();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void uploadRejectsCompletedCaseAndRecordsRefusal() {
        caseFile.setStatus(CaseStatus.COMPLETED);
        when(caseService.requireAccessible(caseId)).thenReturn(caseFile);
        MockMultipartFile file = pdfFile();

        assertThatThrownBy(() -> service.upload(caseId, "PASSPORT", file))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getMessageKey())
                .isEqualTo("error.case.not_modifiable");
        verify(documentUploadRefusalService)
                .record(caseFile, "PASSPORT", file, "error.case.not_modifiable");
        verify(objectStorage, never()).put(any(), any(), any());
    }

    @Test
    void uploadRejectsUnknownRequirement() {
        when(caseService.requireAccessible(caseId)).thenReturn(caseFile);
        MockMultipartFile file = pdfFile();

        assertThatThrownBy(() -> service.upload(caseId, "UNKNOWN", file))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getMessageKey())
                .isEqualTo("error.document.requirement_unknown");
        verify(documentUploadRefusalService)
                .record(caseFile, "UNKNOWN", file, "error.document.requirement_unknown");
    }

    @Test
    void uploadRejectsDisallowedContentType() {
        when(caseService.requireAccessible(caseId)).thenReturn(caseFile);
        MockMultipartFile file =
                new MockMultipartFile("file", "x.exe", "application/octet-stream", new byte[] {1, 2, 3});

        assertThatThrownBy(() -> service.upload(caseId, "PASSPORT", file))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getMessageKey())
                .isEqualTo("error.document.content_type_not_allowed");
    }

    @Test
    void uploadRejectsEmptyFile() {
        when(caseService.requireAccessible(caseId)).thenReturn(caseFile);
        MockMultipartFile file = new MockMultipartFile("file", "empty.pdf", "application/pdf", new byte[0]);

        assertThatThrownBy(() -> service.upload(caseId, "PASSPORT", file))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getMessageKey())
                .isEqualTo("error.document.file_required");
    }

    @Test
    void uploadPersistsDocumentAdvancesCaseAndEmitsOutbox() {
        when(caseService.requireAccessible(caseId)).thenReturn(caseFile);
        MockMultipartFile file = pdfFile();
        when(caseDocumentRepository.existsDuplicateHash(eq(orgId), any(), eq(caseFile.getApplicant().getId())))
                .thenReturn(false);
        when(caseDocumentRepository.saveAndFlush(any(CaseDocument.class))).thenAnswer(inv -> {
            CaseDocument document = inv.getArgument(0);
            document.setId(UUID.randomUUID());
            return document;
        });
        DocumentResponse mapped = org.mockito.Mockito.mock(DocumentResponse.class);
        when(documentResponseFactory.toResponse(any(CaseDocument.class))).thenReturn(mapped);

        DocumentResponse response = service.upload(caseId, "PASSPORT", file);

        assertThat(response).isSameAs(mapped);
        assertThat(caseFile.getStatus()).isEqualTo(CaseStatus.IN_PROGRESS);
        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(objectStorage).put(keyCaptor.capture(), any(byte[].class), eq("application/pdf"));
        assertThat(keyCaptor.getValue()).contains(orgId.toString());
        verify(documentHashAlertService).flagCrossApplicantDuplicates(any(CaseDocument.class));
        verify(outboxAppender).appendDocumentUploaded(eq(caseId), any());
        verify(documentUploadRefusalService, never()).record(any(), any(), any(), any());
    }

    @Test
    void uploadRollsBackStorageWhenPersistFails() {
        when(caseService.requireAccessible(caseId)).thenReturn(caseFile);
        MockMultipartFile file = pdfFile();
        when(caseDocumentRepository.existsDuplicateHash(eq(orgId), any(), eq(caseFile.getApplicant().getId())))
                .thenReturn(false);
        when(caseDocumentRepository.saveAndFlush(any(CaseDocument.class)))
                .thenThrow(new RuntimeException("db down"));

        assertThatThrownBy(() -> service.upload(caseId, "PASSPORT", file)).isInstanceOf(RuntimeException.class);
        verify(objectStorage).put(any(), any(), any());
        verify(objectStorage).delete(any());
    }

    @Test
    void listMapsDocumentsForAccessibleCase() {
        when(caseService.requireAccessible(caseId)).thenReturn(caseFile);
        CaseDocument document = new CaseDocument();
        document.setId(UUID.randomUUID());
        when(caseDocumentRepository.findByCaseFile_IdOrderByCreatedAtAsc(caseId)).thenReturn(List.of(document));
        DocumentResponse mapped = org.mockito.Mockito.mock(DocumentResponse.class);
        when(documentResponseFactory.toResponse(document)).thenReturn(mapped);

        assertThat(service.list(caseId)).containsExactly(mapped);
    }

    @Test
    void downloadRejectsUnknownDocument() {
        UUID documentId = UUID.randomUUID();
        when(caseService.requireAccessible(caseId)).thenReturn(caseFile);
        when(caseDocumentRepository.findByIdAndCaseFile_Id(documentId, caseId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.download(caseId, documentId))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getMessageKey())
                .isEqualTo("error.document.not_found");
    }

    @Test
    void downloadReadsStorageAndLogsAccess() {
        UUID documentId = UUID.randomUUID();
        CaseDocument document = new CaseDocument();
        document.setId(documentId);
        document.setCaseFile(caseFile);
        document.setOriginalFilename("passport.pdf");
        document.setContentType("application/pdf");
        document.setStorageKey("org/case/doc.pdf");
        document.setStatus(DocumentStatus.UPLOADED);
        when(caseService.requireAccessible(caseId)).thenReturn(caseFile);
        when(caseDocumentRepository.findByIdAndCaseFile_Id(documentId, caseId)).thenReturn(Optional.of(document));
        when(objectStorage.get("org/case/doc.pdf")).thenReturn(new byte[] {9, 9, 9});

        DocumentContent content = service.download(caseId, documentId);

        assertThat(content.originalFilename()).isEqualTo("passport.pdf");
        assertThat(content.contentType()).isEqualTo("application/pdf");
        assertThat(content.bytes()).containsExactly(9, 9, 9);
        ArgumentCaptor<DocumentAccessLog> logCaptor = ArgumentCaptor.forClass(DocumentAccessLog.class);
        verify(documentAccessLogRepository).save(logCaptor.capture());
        assertThat(logCaptor.getValue().getAction()).isEqualTo(DocumentAccessAction.DOWNLOAD);
        assertThat(logCaptor.getValue().getAccessedBy()).isEqualTo("citizen-1");
    }

    private CaseFile openCase() {
        Applicant applicant = new Applicant();
        applicant.setId(UUID.randomUUID());
        applicant.setKeycloakSubject("citizen-1");
        ProcedureVersion version = new ProcedureVersion();
        version.setId(UUID.randomUUID());
        version.setDocumentRequirements(List.of(Map.of("code", "PASSPORT", "labelI18n", Map.of("fr", "Passeport"))));
        CaseFile file = new CaseFile();
        file.setId(caseId);
        file.setOrganizationId(orgId);
        file.setStatus(CaseStatus.CREATED);
        file.setApplicant(applicant);
        file.setProcedureVersion(version);
        file.setReference("KV-2026-DOC0001");
        return file;
    }

    private static MockMultipartFile pdfFile() {
        return new MockMultipartFile("file", "passport.pdf", "application/pdf", new byte[] {1, 2, 3, 4});
    }

    private static void authenticateAsCitizen() {
        Jwt jwt = Jwt.withTokenValue("test")
                .header("alg", "none")
                .subject("citizen-1")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .claim("preferred_username", "citizen.dev")
                .build();
        SecurityContextHolder.getContext()
                .setAuthentication(new JwtAuthenticationToken(
                        jwt, List.of(new SimpleGrantedAuthority("ROLE_CITIZEN"))));
    }
}
