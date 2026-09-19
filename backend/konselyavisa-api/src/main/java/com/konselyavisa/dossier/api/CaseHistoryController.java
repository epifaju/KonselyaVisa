package com.konselyavisa.dossier.api;

import com.konselyavisa.common.api.ApiResponse;
import com.konselyavisa.dossier.CaseHistoryService;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/cases")
public class CaseHistoryController {

    private final CaseHistoryService caseHistoryService;

    public CaseHistoryController(CaseHistoryService caseHistoryService) {
        this.caseHistoryService = caseHistoryService;
    }

    @GetMapping("/{id}/history")
    @PreAuthorize("hasAnyRole('AGENT', 'SUPERVISOR', 'BUSINESS_ADMIN', 'PLATFORM_ADMIN')")
    public ApiResponse<List<CaseHistoryEventResponse>> history(@PathVariable UUID id) {
        return ApiResponse.ok(caseHistoryService.list(id));
    }
}
