package com.konselyavisa.document.api;

import com.konselyavisa.common.api.ApiResponse;
import com.konselyavisa.document.DocumentUploadRefusalService;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/cases/{caseId}/upload-refusals")
public class DocumentUploadRefusalController {

    private final DocumentUploadRefusalService documentUploadRefusalService;

    public DocumentUploadRefusalController(DocumentUploadRefusalService documentUploadRefusalService) {
        this.documentUploadRefusalService = documentUploadRefusalService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('AGENT', 'SUPERVISOR', 'BUSINESS_ADMIN', 'PLATFORM_ADMIN')")
    public ApiResponse<List<DocumentUploadRefusalResponse>> list(@PathVariable UUID caseId) {
        return ApiResponse.ok(documentUploadRefusalService.listForCase(caseId));
    }
}
