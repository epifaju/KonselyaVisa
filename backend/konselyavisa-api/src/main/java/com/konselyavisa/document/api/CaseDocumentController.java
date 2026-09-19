package com.konselyavisa.document.api;

import com.konselyavisa.common.api.ApiResponse;
import com.konselyavisa.document.DocumentContent;
import com.konselyavisa.document.DocumentService;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/cases/{caseId}/documents")
public class CaseDocumentController {

    private final DocumentService documentService;

    public CaseDocumentController(DocumentService documentService) {
        this.documentService = documentService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('CITIZEN', 'COMPANY_USER', 'AGENT', 'SUPERVISOR', 'BUSINESS_ADMIN', 'PLATFORM_ADMIN')")
    public ApiResponse<DocumentResponse> upload(
            @PathVariable UUID caseId,
            @RequestParam String requirementCode,
            @RequestParam("file") MultipartFile file) {
        return ApiResponse.ok(documentService.upload(caseId, requirementCode, file), "document.uploaded");
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('CITIZEN', 'COMPANY_USER', 'AGENT', 'SUPERVISOR', 'BUSINESS_ADMIN', 'PLATFORM_ADMIN')")
    public ApiResponse<List<DocumentResponse>> list(@PathVariable UUID caseId) {
        return ApiResponse.ok(documentService.list(caseId));
    }

    @GetMapping("/{documentId}/content")
    @PreAuthorize("hasAnyRole('CITIZEN', 'COMPANY_USER', 'AGENT', 'SUPERVISOR', 'BUSINESS_ADMIN', 'PLATFORM_ADMIN')")
    public ResponseEntity<byte[]> download(@PathVariable UUID caseId, @PathVariable UUID documentId) {
        DocumentContent content = documentService.download(caseId, documentId);
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(content.originalFilename(), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .contentType(MediaType.parseMediaType(content.contentType()))
                .body(content.bytes());
    }
}
