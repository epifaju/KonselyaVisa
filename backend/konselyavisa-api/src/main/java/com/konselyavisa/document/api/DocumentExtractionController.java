package com.konselyavisa.document.api;

import com.konselyavisa.common.api.ApiResponse;
import com.konselyavisa.document.DocumentExtractionService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/cases/{caseId}/documents/{documentId}")
public class DocumentExtractionController {

    private final DocumentExtractionService documentExtractionService;

    public DocumentExtractionController(DocumentExtractionService documentExtractionService) {
        this.documentExtractionService = documentExtractionService;
    }

    @PostMapping("/extraction")
    @PreAuthorize("hasAnyRole('AGENT', 'SUPERVISOR', 'BUSINESS_ADMIN', 'PLATFORM_ADMIN')")
    public ApiResponse<DocumentResponse> saveManual(
            @PathVariable UUID caseId,
            @PathVariable UUID documentId,
            @Valid @RequestBody SaveDocumentExtractionRequest request) {
        return ApiResponse.ok(documentExtractionService.saveManual(caseId, documentId, request), "document.extracted");
    }
}
