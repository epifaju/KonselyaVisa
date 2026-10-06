package com.konselyavisa.document;

import com.konselyavisa.common.exception.BusinessException;
import com.konselyavisa.document.api.DocumentHashAlertResponse;
import com.konselyavisa.document.api.DocumentHashMatchResponse;
import com.konselyavisa.document.domain.CaseDocument;
import com.konselyavisa.document.persistence.CaseDocumentRepository;
import com.konselyavisa.dossier.CaseFile;
import com.konselyavisa.dossier.CaseService;
import com.konselyavisa.identity.CurrentUser;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DocumentHashAlertService {

    private final CaseService caseService;
    private final CaseDocumentRepository caseDocumentRepository;

    public DocumentHashAlertService(CaseService caseService, CaseDocumentRepository caseDocumentRepository) {
        this.caseService = caseService;
        this.caseDocumentRepository = caseDocumentRepository;
    }

    @Transactional
    public void flagCrossApplicantDuplicates(CaseDocument document) {
        List<CaseDocument> sameHash = caseDocumentRepository.findByOrganizationIdAndSha256In(
                document.getOrganizationId(), List.of(document.getSha256()));
        long distinctApplicants = sameHash.stream()
                .map(item -> item.getCaseFile().getApplicant().getId())
                .distinct()
                .count();
        if (distinctApplicants < 2) {
            return;
        }
        for (CaseDocument item : sameHash) {
            item.setDuplicateHash(true);
        }
    }

    @Transactional(readOnly = true)
    public List<DocumentHashAlertResponse> listForCase(UUID caseId) {
        if (CurrentUser.isSelfScoped() || CurrentUser.isCompanyWorkspace()) {
            throw BusinessException.forbidden("error.access.denied");
        }
        CaseFile caseFile = caseService.requireAccessible(caseId);
        List<CaseDocument> documents = caseDocumentRepository.findByCaseFile_IdOrderByCreatedAtAsc(caseId);
        if (documents.isEmpty()) {
            return List.of();
        }
        List<String> hashes = documents.stream().map(CaseDocument::getSha256).distinct().toList();
        Map<String, List<CaseDocument>> byHash = caseDocumentRepository
                .findByOrganizationIdAndSha256In(caseFile.getOrganizationId(), hashes)
                .stream()
                .collect(Collectors.groupingBy(CaseDocument::getSha256, LinkedHashMap::new, Collectors.toList()));
        UUID applicantId = caseFile.getApplicant().getId();
        List<DocumentHashAlertResponse> alerts = new ArrayList<>();
        for (CaseDocument document : documents) {
            List<DocumentHashMatchResponse> matches = byHash.getOrDefault(document.getSha256(), List.of()).stream()
                    .filter(other -> !other.getCaseFile().getApplicant().getId().equals(applicantId))
                    .collect(Collectors.toMap(
                            other -> other.getCaseFile().getId(),
                            Function.identity(),
                            (first, ignored) -> first,
                            LinkedHashMap::new))
                    .values()
                    .stream()
                    .map(DocumentHashAlertService::toMatch)
                    .toList();
            if (!matches.isEmpty()) {
                alerts.add(new DocumentHashAlertResponse(document.getId(), document.getRequirementCode(), matches));
            }
        }
        return alerts;
    }

    private static DocumentHashMatchResponse toMatch(CaseDocument document) {
        String displayName = document.getCaseFile().getApplicant().getDisplayName();
        if (displayName == null || displayName.isBlank()) {
            displayName = "—";
        }
        return new DocumentHashMatchResponse(
                document.getCaseFile().getId(),
                document.getCaseFile().getReference(),
                displayName,
                document.getRequirementCode());
    }
}
