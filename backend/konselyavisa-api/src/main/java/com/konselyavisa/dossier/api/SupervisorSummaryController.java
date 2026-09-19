package com.konselyavisa.dossier.api;

import com.konselyavisa.common.api.ApiResponse;
import com.konselyavisa.dossier.SupervisorSummaryService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/supervisor")
public class SupervisorSummaryController {

    private final SupervisorSummaryService supervisorSummaryService;

    public SupervisorSummaryController(SupervisorSummaryService supervisorSummaryService) {
        this.supervisorSummaryService = supervisorSummaryService;
    }

    @GetMapping("/summary")
    @PreAuthorize("hasAnyRole('SUPERVISOR', 'BUSINESS_ADMIN', 'PLATFORM_ADMIN')")
    public ApiResponse<SupervisorSummaryResponse> summary() {
        return ApiResponse.ok(supervisorSummaryService.summarize());
    }
}
