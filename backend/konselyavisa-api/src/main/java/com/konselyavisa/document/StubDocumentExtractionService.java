package com.konselyavisa.document;

import com.konselyavisa.common.exception.BusinessException;
import com.konselyavisa.document.crypto.ExtractedFieldsCrypto;
import com.konselyavisa.document.api.DocumentResponse;
import com.konselyavisa.document.api.SaveDocumentExtractionRequest;
import com.konselyavisa.document.domain.CaseDocument;
import com.konselyavisa.document.domain.ExtractionSource;
import com.konselyavisa.document.persistence.CaseDocumentRepository;
import com.konselyavisa.dossier.CaseFile;
import com.konselyavisa.dossier.CaseService;
import com.konselyavisa.dossier.CaseStatus;
import com.konselyavisa.identity.CurrentUser;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StubDocumentExtractionService implements DocumentExtractionService {

    private static final int MAX_ENTRIES = 20;
    private static final int MAX_KEY_LENGTH = 50;
    private static final int MAX_VALUE_LENGTH = 200;

    private final CaseService caseService;
    private final CaseDocumentRepository caseDocumentRepository;
    private final DocumentResponseFactory documentResponseFactory;
    private final ExtractedFieldsCrypto extractedFieldsCrypto;

    public StubDocumentExtractionService(
            CaseService caseService,
            CaseDocumentRepository caseDocumentRepository,
            DocumentResponseFactory documentResponseFactory,
            ExtractedFieldsCrypto extractedFieldsCrypto) {
        this.caseService = caseService;
        this.caseDocumentRepository = caseDocumentRepository;
        this.documentResponseFactory = documentResponseFactory;
        this.extractedFieldsCrypto = extractedFieldsCrypto;
    }

    @Override
    @Transactional
    public DocumentResponse saveManual(UUID caseId, UUID documentId, SaveDocumentExtractionRequest request) {
        if (CurrentUser.isSelfScoped()) {
            throw BusinessException.forbidden("error.access.denied");
        }
        CaseFile caseFile = caseService.requireAccessible(caseId);
        if (caseFile.getStatus() == CaseStatus.CANCELLED || caseFile.getStatus() == CaseStatus.COMPLETED) {
            throw BusinessException.badRequest("error.case.not_modifiable");
        }
        CaseDocument document = caseDocumentRepository
                .findByIdAndCaseFile_Id(documentId, caseId)
                .orElseThrow(() -> BusinessException.notFound("error.document.not_found"));
        document.setExtractedFieldsCipher(extractedFieldsCrypto.encrypt(sanitizeRequired(request.extractedFields())));
        document.setDocumentValidations(sanitizeOptional(request.documentValidations()));
        document.setAiConfidence(null);
        document.setExtractionSource(ExtractionSource.MANUAL);
        return documentResponseFactory.toResponse(document);
    }

    static Map<String, String> sanitizeRequired(Map<String, String> source) {
        Map<String, String> clean = sanitizeOptional(source);
        if (clean.isEmpty()) {
            throw BusinessException.badRequest("error.document.extraction_required");
        }
        return clean;
    }

    static Map<String, String> sanitizeOptional(Map<String, String> source) {
        if (source == null || source.isEmpty()) {
            return Map.of();
        }
        if (source.size() > MAX_ENTRIES) {
            throw BusinessException.badRequest("error.document.extraction_invalid");
        }
        Map<String, String> clean = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : source.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) {
                throw BusinessException.badRequest("error.document.extraction_invalid");
            }
            String key = entry.getKey().trim().toLowerCase(Locale.ROOT);
            String value = entry.getValue().trim();
            if (key.isEmpty()
                    || value.isEmpty()
                    || key.length() > MAX_KEY_LENGTH
                    || value.length() > MAX_VALUE_LENGTH
                    || !key.matches("[a-z][a-z0-9_]{0,49}")) {
                throw BusinessException.badRequest("error.document.extraction_invalid");
            }
            clean.put(key, value);
        }
        return Map.copyOf(clean);
    }
}
