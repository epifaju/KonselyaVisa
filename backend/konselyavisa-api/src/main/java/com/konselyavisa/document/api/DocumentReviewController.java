package com.konselyavisa.document.api;

import com.konselyavisa.common.api.ApiResponse;
import com.konselyavisa.document.DocumentReviewService;
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
public class DocumentReviewController {

    private final DocumentReviewService documentReviewService;

    public DocumentReviewController(DocumentReviewService documentReviewService) {
        this.documentReviewService = documentReviewService;
    }

    @PostMapping("/accept")
    @PreAuthorize("hasAnyRole('AGENT', 'SUPERVISOR', 'BUSINESS_ADMIN', 'PLATFORM_ADMIN')")
    public ApiResponse<DocumentResponse> accept(@PathVariable UUID caseId, @PathVariable UUID documentId) {
        return ApiResponse.ok(documentReviewService.accept(caseId, documentId), "document.accepted");
    }

    @PostMapping("/request-correction")
    @PreAuthorize("hasAnyRole('AGENT', 'SUPERVISOR', 'BUSINESS_ADMIN', 'PLATFORM_ADMIN')")
    public ApiResponse<DocumentResponse> requestCorrection(
            @PathVariable UUID caseId,
            @PathVariable UUID documentId,
            @Valid @RequestBody DocumentReviewRequest request) {
        return ApiResponse.ok(
                documentReviewService.requestCorrection(caseId, documentId, request), "document.correction_requested");
    }

    @PostMapping("/reject")
    @PreAuthorize("hasAnyRole('AGENT', 'SUPERVISOR', 'BUSINESS_ADMIN', 'PLATFORM_ADMIN')")
    public ApiResponse<DocumentResponse> reject(
            @PathVariable UUID caseId,
            @PathVariable UUID documentId,
            @Valid @RequestBody DocumentReviewRequest request) {
        return ApiResponse.ok(documentReviewService.reject(caseId, documentId, request), "document.rejected");
    }
}
