package com.konselyavisa.document;

import com.konselyavisa.common.exception.BusinessException;
import com.konselyavisa.document.api.DocumentMapper;
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
import com.konselyavisa.identity.CurrentUser;
import com.konselyavisa.outbox.OutboxAppender;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class DocumentService {

    private final CaseService caseService;
    private final CaseDocumentRepository caseDocumentRepository;
    private final DocumentAccessLogRepository documentAccessLogRepository;
    private final ObjectStorage objectStorage;
    private final OutboxAppender outboxAppender;
    private final DocumentMapper documentMapper;

    public DocumentService(
            CaseService caseService,
            CaseDocumentRepository caseDocumentRepository,
            DocumentAccessLogRepository documentAccessLogRepository,
            ObjectStorage objectStorage,
            OutboxAppender outboxAppender,
            DocumentMapper documentMapper) {
        this.caseService = caseService;
        this.caseDocumentRepository = caseDocumentRepository;
        this.documentAccessLogRepository = documentAccessLogRepository;
        this.objectStorage = objectStorage;
        this.outboxAppender = outboxAppender;
        this.documentMapper = documentMapper;
    }

    @Transactional
    public DocumentResponse upload(UUID caseId, String requirementCode, MultipartFile file) {
        CaseFile caseFile = caseService.requireAccessible(caseId);
        assertCaseAcceptsDocuments(caseFile);
        if (!DocumentRequirementCodes.isDeclared(caseFile.getProcedureVersion(), requirementCode)) {
            throw BusinessException.badRequest("error.document.requirement_unknown");
        }
        byte[] content = readFile(file);
        String contentType = file.getContentType();
        if (!DocumentFiles.isAllowedContentType(contentType)) {
            throw BusinessException.badRequest("error.document.content_type_not_allowed");
        }
        String originalFilename = DocumentFiles.sanitizeOriginalFilename(file.getOriginalFilename());
        String sha256 = DocumentFiles.sha256Hex(content);
        String storageKey = DocumentFiles.storageKey(
                caseFile.getOrganizationId(),
                caseFile.getId(),
                UUID.randomUUID(),
                originalFilename);
        boolean duplicateHash = caseDocumentRepository.existsDuplicateHash(
                caseFile.getOrganizationId(), sha256, caseFile.getApplicant().getId());

        objectStorage.put(storageKey, content, contentType);
        try {
            CaseDocument document = new CaseDocument();
            document.setCaseFile(caseFile);
            document.setRequirementCode(requirementCode);
            document.setOriginalFilename(originalFilename);
            document.setContentType(contentType);
            document.setSizeBytes(content.length);
            document.setSha256(sha256);
            document.setStorageKey(storageKey);
            document.setStatus(DocumentStatus.UPLOADED);
            document.setDuplicateHash(duplicateHash);
            caseDocumentRepository.saveAndFlush(document);
            if (caseFile.getStatus() == CaseStatus.CREATED) {
                caseFile.setStatus(CaseStatus.IN_PROGRESS);
            }
            outboxAppender.appendDocumentUploaded(caseFile.getId(), uploadedPayload(document));
            return documentMapper.toResponse(document);
        } catch (RuntimeException ex) {
            objectStorage.delete(storageKey);
            throw ex;
        }
    }

    @Transactional(readOnly = true)
    public List<DocumentResponse> list(UUID caseId) {
        caseService.requireAccessible(caseId);
        return caseDocumentRepository.findByCaseFile_IdOrderByCreatedAtAsc(caseId).stream()
                .map(documentMapper::toResponse)
                .toList();
    }

    @Transactional
    public DocumentContent download(UUID caseId, UUID documentId) {
        caseService.requireAccessible(caseId);
        CaseDocument document = caseDocumentRepository
                .findByIdAndCaseFile_Id(documentId, caseId)
                .orElseThrow(() -> BusinessException.notFound("error.document.not_found"));
        byte[] bytes = objectStorage.get(document.getStorageKey());
        DocumentAccessLog accessLog = new DocumentAccessLog();
        accessLog.setDocument(document);
        accessLog.setAccessedBy(CurrentUser.subject());
        accessLog.setAction(DocumentAccessAction.DOWNLOAD);
        documentAccessLogRepository.save(accessLog);
        return new DocumentContent(document.getOriginalFilename(), document.getContentType(), bytes);
    }

    private static void assertCaseAcceptsDocuments(CaseFile caseFile) {
        if (caseFile.getStatus() == CaseStatus.CANCELLED || caseFile.getStatus() == CaseStatus.COMPLETED) {
            throw BusinessException.badRequest("error.case.not_modifiable");
        }
    }

    private static byte[] readFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw BusinessException.badRequest("error.document.file_required");
        }
        if (file.getSize() > DocumentFiles.MAX_BYTES) {
            throw BusinessException.badRequest("error.document.too_large");
        }
        try {
            byte[] content = file.getBytes();
            if (content.length == 0) {
                throw BusinessException.badRequest("error.document.file_required");
            }
            if (content.length > DocumentFiles.MAX_BYTES) {
                throw BusinessException.badRequest("error.document.too_large");
            }
            return content;
        } catch (IOException ex) {
            throw BusinessException.badRequest("error.document.unreadable");
        }
    }

    private static Map<String, Object> uploadedPayload(CaseDocument document) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("caseId", document.getCaseFile().getId().toString());
        payload.put("documentId", document.getId().toString());
        payload.put("requirementCode", document.getRequirementCode());
        payload.put("contentType", document.getContentType());
        payload.put("sizeBytes", document.getSizeBytes());
        payload.put("sha256", document.getSha256());
        payload.put("duplicateHash", document.isDuplicateHash());
        return payload;
    }
}
