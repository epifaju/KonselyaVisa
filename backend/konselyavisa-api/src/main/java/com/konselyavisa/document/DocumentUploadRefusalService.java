package com.konselyavisa.document;

import com.konselyavisa.common.exception.BusinessException;
import com.konselyavisa.document.api.DocumentUploadRefusalMapper;
import com.konselyavisa.document.api.DocumentUploadRefusalResponse;
import com.konselyavisa.document.domain.DocumentUploadRefusal;
import com.konselyavisa.document.persistence.DocumentUploadRefusalRepository;
import com.konselyavisa.dossier.CaseFile;
import com.konselyavisa.dossier.CaseFileRepository;
import com.konselyavisa.dossier.CaseService;
import com.konselyavisa.identity.CurrentUser;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

@Service
public class DocumentUploadRefusalService {

    private final CaseService caseService;
    private final CaseFileRepository caseFileRepository;
    private final DocumentUploadRefusalRepository documentUploadRefusalRepository;
    private final DocumentUploadRefusalMapper documentUploadRefusalMapper;
    private final TransactionTemplate requiresNew;

    public DocumentUploadRefusalService(
            CaseService caseService,
            CaseFileRepository caseFileRepository,
            DocumentUploadRefusalRepository documentUploadRefusalRepository,
            DocumentUploadRefusalMapper documentUploadRefusalMapper,
            PlatformTransactionManager transactionManager) {
        this.caseService = caseService;
        this.caseFileRepository = caseFileRepository;
        this.documentUploadRefusalRepository = documentUploadRefusalRepository;
        this.documentUploadRefusalMapper = documentUploadRefusalMapper;
        this.requiresNew = new TransactionTemplate(transactionManager);
        this.requiresNew.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public void record(CaseFile caseFile, String requirementCode, MultipartFile file, String reasonKey) {
        RefusalFileSnapshot snapshot = snapshot(file);
        String truncatedRequirement = truncate(requirementCode, 50);
        String truncatedReason = truncate(reasonKey, 120);
        if (truncatedReason == null || truncatedReason.isBlank()) {
            truncatedReason = "error.document.upload_refused";
        }
        String finalReason = truncatedReason;
        UUID caseId = caseFile.getId();
        UUID organizationId = caseFile.getOrganizationId();
        requiresNew.executeWithoutResult(status -> {
            DocumentUploadRefusal refusal = new DocumentUploadRefusal();
            refusal.setOrganizationId(organizationId);
            refusal.setCaseFile(caseFileRepository.getReferenceById(caseId));
            refusal.setRequirementCode(truncatedRequirement);
            refusal.setReasonKey(finalReason);
            refusal.setContentType(truncate(snapshot.contentType(), 100));
            refusal.setSizeBytes(snapshot.sizeBytes());
            refusal.setSha256(snapshot.sha256());
            refusal.setOriginalFilename(snapshot.originalFilename());
            refusal.setAttemptedBy(CurrentUser.subject());
            documentUploadRefusalRepository.save(refusal);
        });
    }

    @Transactional(readOnly = true)
    public List<DocumentUploadRefusalResponse> listForCase(UUID caseId) {
        if (CurrentUser.isSelfScoped() || CurrentUser.isCompanyWorkspace()) {
            throw BusinessException.forbidden("error.access.denied");
        }
        caseService.requireAccessible(caseId);
        return documentUploadRefusalRepository.findByCaseFile_IdOrderByCreatedAtDesc(caseId).stream()
                .map(documentUploadRefusalMapper::toResponse)
                .toList();
    }

    private static RefusalFileSnapshot snapshot(MultipartFile file) {
        if (file == null) {
            return RefusalFileSnapshot.empty();
        }
        String contentType = file.getContentType();
        String originalFilename = DocumentFiles.sanitizeOriginalFilename(file.getOriginalFilename());
        long reportedSize = file.getSize();
        Long sizeBytes = reportedSize >= 0 ? reportedSize : null;
        String sha256 = null;
        if (reportedSize > 0 && reportedSize <= DocumentFiles.MAX_BYTES) {
            try {
                byte[] content = file.getBytes();
                if (content.length > 0 && content.length <= DocumentFiles.MAX_BYTES) {
                    sha256 = DocumentFiles.sha256Hex(content);
                    sizeBytes = (long) content.length;
                }
            } catch (IOException ignored) {
                // Journal metadata only — unreadability is already the refusal reason.
            }
        }
        return new RefusalFileSnapshot(contentType, sizeBytes, sha256, originalFilename);
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        return trimmed.length() <= max ? trimmed : trimmed.substring(0, max);
    }

    private record RefusalFileSnapshot(
            String contentType, Long sizeBytes, String sha256, String originalFilename) {

        static RefusalFileSnapshot empty() {
            return new RefusalFileSnapshot(null, null, null, null);
        }
    }
}
