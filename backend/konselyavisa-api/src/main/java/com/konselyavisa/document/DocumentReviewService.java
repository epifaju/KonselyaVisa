package com.konselyavisa.document;

import com.konselyavisa.common.exception.BusinessException;
import com.konselyavisa.document.api.DocumentMapper;
import com.konselyavisa.document.api.DocumentResponse;
import com.konselyavisa.document.api.DocumentReviewRequest;
import com.konselyavisa.document.ReviewMessageKeys;
import com.konselyavisa.document.domain.CaseDocument;
import com.konselyavisa.document.domain.DocumentStatus;
import com.konselyavisa.document.persistence.CaseDocumentRepository;
import com.konselyavisa.dossier.CaseFile;
import com.konselyavisa.dossier.CaseService;
import com.konselyavisa.dossier.CaseStatus;
import com.konselyavisa.identity.CurrentUser;
import com.konselyavisa.outbox.OutboxAppender;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DocumentReviewService {

    private final CaseService caseService;
    private final CaseDocumentRepository caseDocumentRepository;
    private final OutboxAppender outboxAppender;
    private final DocumentMapper documentMapper;

    public DocumentReviewService(
            CaseService caseService,
            CaseDocumentRepository caseDocumentRepository,
            OutboxAppender outboxAppender,
            DocumentMapper documentMapper) {
        this.caseService = caseService;
        this.caseDocumentRepository = caseDocumentRepository;
        this.outboxAppender = outboxAppender;
        this.documentMapper = documentMapper;
    }

    @Transactional
    public DocumentResponse accept(UUID caseId, UUID documentId) {
        CaseFile caseFile = requireStaffCase(caseId);
        CaseDocument document = requireDocument(caseId, documentId);
        if (document.getStatus() == DocumentStatus.REJECTED) {
            throw BusinessException.badRequest("error.document.not_reviewable");
        }
        document.setStatus(DocumentStatus.ACCEPTED);
        if (caseFile.getStatus() == CaseStatus.CORRECTION_REQUESTED
                && !caseDocumentRepository.existsByCaseFile_IdAndStatus(
                        caseId, DocumentStatus.CORRECTION_REQUESTED)) {
            caseFile.setStatus(CaseStatus.IN_PROGRESS);
        }
        return documentMapper.toResponse(document);
    }

    @Transactional
    public DocumentResponse requestCorrection(UUID caseId, UUID documentId, DocumentReviewRequest request) {
        CaseFile caseFile = requireStaffCase(caseId);
        CaseDocument document = requireDocument(caseId, documentId);
        if (document.getStatus() == DocumentStatus.REJECTED) {
            throw BusinessException.badRequest("error.document.not_reviewable");
        }
        document.setStatus(DocumentStatus.CORRECTION_REQUESTED);
        document.setReviewMessageKey(ReviewMessageKeys.fromReason(request.reason()));
        if (caseFile.getStatus() != CaseStatus.CANCELLED && caseFile.getStatus() != CaseStatus.COMPLETED) {
            caseFile.setStatus(CaseStatus.CORRECTION_REQUESTED);
        }
        outboxAppender.appendCorrectionRequested(caseFile.getId(), correctionPayload(document, request.reason()));
        return documentMapper.toResponse(document);
    }

    @Transactional
    public DocumentResponse reject(UUID caseId, UUID documentId, DocumentReviewRequest request) {
        requireStaffCase(caseId);
        CaseDocument document = requireDocument(caseId, documentId);
        if (document.getStatus() == DocumentStatus.ACCEPTED) {
            throw BusinessException.badRequest("error.document.not_reviewable");
        }
        document.setStatus(DocumentStatus.REJECTED);
        document.setReviewMessageKey(ReviewMessageKeys.fromReason(request.reason()));
        outboxAppender.appendCorrectionRequested(document.getCaseFile().getId(), rejectPayload(document, request.reason()));
        return documentMapper.toResponse(document);
    }

    private CaseFile requireStaffCase(UUID caseId) {
        if (CurrentUser.isSelfScoped()) {
            throw BusinessException.forbidden("error.access.denied");
        }
        CaseFile caseFile = caseService.requireAccessible(caseId);
        if (caseFile.getStatus() == CaseStatus.CANCELLED || caseFile.getStatus() == CaseStatus.COMPLETED) {
            throw BusinessException.badRequest("error.case.not_modifiable");
        }
        return caseFile;
    }

    private CaseDocument requireDocument(UUID caseId, UUID documentId) {
        return caseDocumentRepository
                .findByIdAndCaseFile_Id(documentId, caseId)
                .orElseThrow(() -> BusinessException.notFound("error.document.not_found"));
    }

    private static Map<String, Object> correctionPayload(CaseDocument document, String reason) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("caseId", document.getCaseFile().getId().toString());
        payload.put("documentId", document.getId().toString());
        payload.put("requirementCode", document.getRequirementCode());
        payload.put("reason", reason);
        payload.put("messageKey", document.getReviewMessageKey());
        payload.put("decision", "CORRECTION_REQUESTED");
        return payload;
    }

    private static Map<String, Object> rejectPayload(CaseDocument document, String reason) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("caseId", document.getCaseFile().getId().toString());
        payload.put("documentId", document.getId().toString());
        payload.put("requirementCode", document.getRequirementCode());
        payload.put("reason", reason);
        payload.put("messageKey", document.getReviewMessageKey());
        payload.put("decision", "REJECTED");
        return payload;
    }
}
