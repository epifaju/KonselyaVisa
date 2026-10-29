package com.konselyavisa.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.konselyavisa.common.exception.BusinessException;
import com.konselyavisa.document.api.DocumentResponse;
import com.konselyavisa.document.api.SaveDocumentExtractionRequest;
import com.konselyavisa.document.crypto.ExtractedFieldsCrypto;
import com.konselyavisa.document.domain.CaseDocument;
import com.konselyavisa.document.domain.ExtractionSource;
import com.konselyavisa.document.persistence.CaseDocumentRepository;
import com.konselyavisa.dossier.CaseFile;
import com.konselyavisa.dossier.CaseService;
import com.konselyavisa.dossier.CaseStatus;
import java.time.Instant;
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
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

@ExtendWith(MockitoExtension.class)
class StubDocumentExtractionServiceTest {

    @Mock
    private CaseService caseService;

    @Mock
    private CaseDocumentRepository caseDocumentRepository;

    @Mock
    private DocumentResponseFactory documentResponseFactory;

    @Mock
    private ExtractedFieldsCrypto extractedFieldsCrypto;

    private StubDocumentExtractionService service;
    private UUID caseId;
    private UUID documentId;
    private CaseFile caseFile;
    private CaseDocument document;

    @BeforeEach
    void setUp() {
        service = new StubDocumentExtractionService(
                caseService, caseDocumentRepository, documentResponseFactory, extractedFieldsCrypto);
        caseId = UUID.randomUUID();
        documentId = UUID.randomUUID();
        caseFile = new CaseFile();
        caseFile.setStatus(CaseStatus.IN_PROGRESS);
        document = new CaseDocument();
        document.setId(documentId);
        document.setCaseFile(caseFile);
        authenticate("agent-1", "ROLE_AGENT");
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void citizenCannotSaveExtraction() {
        authenticate("citizen-1", "ROLE_CITIZEN");

        assertThatThrownBy(() -> service.saveManual(caseId, documentId, request(Map.of("given_name", "Awa"), null)))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getMessageKey())
                .isEqualTo("error.access.denied");
        verify(caseService, never()).requireAccessible(any());
        verify(extractedFieldsCrypto, never()).encrypt(any());
    }

    @Test
    void closedCaseRejectsExtraction() {
        caseFile.setStatus(CaseStatus.COMPLETED);
        when(caseService.requireAccessible(caseId)).thenReturn(caseFile);

        assertThatThrownBy(() -> service.saveManual(caseId, documentId, request(Map.of("given_name", "Awa"), null)))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getMessageKey())
                .isEqualTo("error.case.not_modifiable");
        verify(caseDocumentRepository, never()).findByIdAndCaseFile_Id(any(), any());
    }

    @Test
    void missingDocumentIsNotFound() {
        when(caseService.requireAccessible(caseId)).thenReturn(caseFile);
        when(caseDocumentRepository.findByIdAndCaseFile_Id(documentId, caseId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.saveManual(caseId, documentId, request(Map.of("given_name", "Awa"), null)))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getMessageKey())
                .isEqualTo("error.document.not_found");
        verify(extractedFieldsCrypto, never()).encrypt(any());
    }

    @Test
    void invalidFieldDoesNotEncrypt() {
        when(caseService.requireAccessible(caseId)).thenReturn(caseFile);
        when(caseDocumentRepository.findByIdAndCaseFile_Id(documentId, caseId)).thenReturn(Optional.of(document));

        assertThatThrownBy(() -> service.saveManual(caseId, documentId, request(Map.of(), null)))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getMessageKey())
                .isEqualTo("error.document.extraction_required");
        verify(extractedFieldsCrypto, never()).encrypt(any());
    }

    @Test
    void agentSaveEncryptsSanitizedFields() {
        when(caseService.requireAccessible(caseId)).thenReturn(caseFile);
        when(caseDocumentRepository.findByIdAndCaseFile_Id(documentId, caseId)).thenReturn(Optional.of(document));
        when(extractedFieldsCrypto.encrypt(Map.of("givenname", "Awa"))).thenReturn(new byte[] {1, 2});
        DocumentResponse response = org.mockito.Mockito.mock(DocumentResponse.class);
        when(documentResponseFactory.toResponse(document)).thenReturn(response);

        DocumentResponse saved = service.saveManual(
                caseId,
                documentId,
                request(Map.of(" GivenName ", " Awa "), Map.of("mrz_valid", "UNCHECKED")));

        assertThat(saved).isSameAs(response);
        assertThat(document.getExtractedFieldsCipher()).containsExactly(1, 2);
        assertThat(document.getDocumentValidations()).containsEntry("mrz_valid", "UNCHECKED");
        assertThat(document.getExtractionSource()).isEqualTo(ExtractionSource.MANUAL);
        assertThat(document.getAiConfidence()).isNull();
    }

    private static SaveDocumentExtractionRequest request(
            Map<String, String> fields, Map<String, String> validations) {
        return new SaveDocumentExtractionRequest(fields, validations);
    }

    private static void authenticate(String subject, String role) {
        Jwt jwt = Jwt.withTokenValue("test")
                .header("alg", "none")
                .subject(subject)
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .build();
        SecurityContextHolder.getContext()
                .setAuthentication(new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority(role))));
    }
}
