package com.konselyavisa.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.konselyavisa.common.exception.BusinessException;
import com.konselyavisa.document.api.DocumentResponse;
import com.konselyavisa.document.api.DocumentReviewRequest;
import com.konselyavisa.document.domain.CaseDocument;
import com.konselyavisa.document.domain.DocumentStatus;
import com.konselyavisa.document.persistence.CaseDocumentRepository;
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
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

@ExtendWith(MockitoExtension.class)
class DocumentReviewServiceTest {

    @Mock
    private CaseService caseService;

    @Mock
    private CaseDocumentRepository caseDocumentRepository;

    @Mock
    private OutboxAppender outboxAppender;

    @Mock
    private DocumentResponseFactory documentResponseFactory;

    @InjectMocks
    private DocumentReviewService service;

    private UUID caseId;
    private UUID documentId;
    private CaseFile caseFile;
    private CaseDocument document;

    @BeforeEach
    void setUp() {
        caseId = UUID.randomUUID();
        documentId = UUID.randomUUID();
        caseFile = new CaseFile();
        caseFile.setId(caseId);
        caseFile.setReference("KV-2026-TEST0001");
        caseFile.setStatus(CaseStatus.IN_PROGRESS);
        document = new CaseDocument();
        document.setId(documentId);
        document.setCaseFile(caseFile);
        document.setRequirementCode("PASSPORT");
        document.setStatus(DocumentStatus.UPLOADED);
        authenticateAsAgent();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void citizenCannotReview() {
        authenticateAsCitizen();
        assertThatThrownBy(() -> service.accept(caseId, documentId))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getMessageKey())
                .isEqualTo("error.access.denied");
        verify(caseService, never()).requireAccessible(any());
    }

    @Test
    void acceptMarksDocumentAccepted() {
        stubAccessibleDocument();
        when(documentResponseFactory.toResponse(document)).thenReturn(dummyResponse(DocumentStatus.ACCEPTED));

        service.accept(caseId, documentId);

        assertThat(document.getStatus()).isEqualTo(DocumentStatus.ACCEPTED);
        assertThat(caseFile.getStatus()).isEqualTo(CaseStatus.IN_PROGRESS);
    }

    @Test
    void acceptClearsCorrectionWhenNoPendingCorrectionsRemain() {
        caseFile.setStatus(CaseStatus.CORRECTION_REQUESTED);
        stubAccessibleDocument();
        when(caseDocumentRepository.existsByCaseFile_IdAndStatus(caseId, DocumentStatus.CORRECTION_REQUESTED))
                .thenReturn(false);
        when(documentResponseFactory.toResponse(document)).thenReturn(dummyResponse(DocumentStatus.ACCEPTED));

        service.accept(caseId, documentId);

        assertThat(document.getStatus()).isEqualTo(DocumentStatus.ACCEPTED);
        assertThat(caseFile.getStatus()).isEqualTo(CaseStatus.IN_PROGRESS);
    }

    @Test
    void acceptRejectedDocumentFails() {
        document.setStatus(DocumentStatus.REJECTED);
        stubAccessibleDocument();

        assertThatThrownBy(() -> service.accept(caseId, documentId))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getMessageKey())
                .isEqualTo("error.document.not_reviewable");
    }

    @Test
    void requestCorrectionUpdatesCaseAndOutbox() {
        stubAccessibleDocument();
        when(documentResponseFactory.toResponse(document)).thenReturn(dummyResponse(DocumentStatus.CORRECTION_REQUESTED));

        service.requestCorrection(caseId, documentId, new DocumentReviewRequest("document.correction.unreadable"));

        assertThat(document.getStatus()).isEqualTo(DocumentStatus.CORRECTION_REQUESTED);
        assertThat(document.getReviewMessageKey()).isEqualTo("document.correction.unreadable");
        assertThat(caseFile.getStatus()).isEqualTo(CaseStatus.CORRECTION_REQUESTED);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> payload = ArgumentCaptor.forClass(Map.class);
        verify(outboxAppender).appendCorrectionRequested(eq(caseId), payload.capture());
        assertThat(payload.getValue())
                .containsEntry("decision", "CORRECTION_REQUESTED")
                .containsEntry("requirementCode", "PASSPORT");
    }

    @Test
    void rejectAcceptedDocumentFails() {
        document.setStatus(DocumentStatus.ACCEPTED);
        stubAccessibleDocument();

        assertThatThrownBy(() -> service.reject(caseId, documentId, new DocumentReviewRequest("fraud")))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getMessageKey())
                .isEqualTo("error.document.not_reviewable");
    }

    @Test
    void rejectEmitsOutboxWithoutChangingCaseStatus() {
        stubAccessibleDocument();
        when(documentResponseFactory.toResponse(document)).thenReturn(dummyResponse(DocumentStatus.REJECTED));

        service.reject(caseId, documentId, new DocumentReviewRequest("document.correction.unreadable"));

        assertThat(document.getStatus()).isEqualTo(DocumentStatus.REJECTED);
        assertThat(caseFile.getStatus()).isEqualTo(CaseStatus.IN_PROGRESS);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> payload = ArgumentCaptor.forClass(Map.class);
        verify(outboxAppender).appendCorrectionRequested(eq(caseId), payload.capture());
        assertThat(payload.getValue()).containsEntry("decision", "REJECTED");
    }

    @Test
    void completedCaseCannotBeReviewed() {
        caseFile.setStatus(CaseStatus.COMPLETED);
        when(caseService.requireAccessible(caseId)).thenReturn(caseFile);

        assertThatThrownBy(() -> service.accept(caseId, documentId))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getMessageKey())
                .isEqualTo("error.case.not_modifiable");
    }

    private void stubAccessibleDocument() {
        when(caseService.requireAccessible(caseId)).thenReturn(caseFile);
        when(caseDocumentRepository.findByIdAndCaseFile_Id(documentId, caseId)).thenReturn(Optional.of(document));
    }

    private static DocumentResponse dummyResponse(DocumentStatus status) {
        return new DocumentResponse(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "PASSPORT",
                "passport.pdf",
                "application/pdf",
                10L,
                "abc",
                status,
                false,
                null,
                Map.of(),
                Map.of(),
                null,
                null,
                Instant.now());
    }

    private static void authenticateAsAgent() {
        Jwt jwt = Jwt.withTokenValue("test")
                .header("alg", "none")
                .subject("agent-1")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .claim("preferred_username", "agent.dev")
                .build();
        SecurityContextHolder.getContext()
                .setAuthentication(new JwtAuthenticationToken(
                        jwt, List.of(new SimpleGrantedAuthority("ROLE_AGENT"))));
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
