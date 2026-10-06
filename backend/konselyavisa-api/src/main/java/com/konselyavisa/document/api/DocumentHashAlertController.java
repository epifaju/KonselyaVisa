package com.konselyavisa.document.api;

import com.konselyavisa.common.api.ApiResponse;
import com.konselyavisa.document.DocumentHashAlertService;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/cases/{caseId}/hash-alerts")
public class DocumentHashAlertController {

    private final DocumentHashAlertService documentHashAlertService;

    public DocumentHashAlertController(DocumentHashAlertService documentHashAlertService) {
        this.documentHashAlertService = documentHashAlertService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('AGENT', 'SUPERVISOR', 'BUSINESS_ADMIN', 'PLATFORM_ADMIN')")
    public ApiResponse<List<DocumentHashAlertResponse>> list(@PathVariable UUID caseId) {
        return ApiResponse.ok(documentHashAlertService.listForCase(caseId));
    }
}
