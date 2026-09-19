package com.konselyavisa.dossier.api;

import com.konselyavisa.common.api.ApiResponse;
import com.konselyavisa.dossier.CompanyCaseService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/company/cases")
public class CompanyCaseController {

    private final CompanyCaseService companyCaseService;

    public CompanyCaseController(CompanyCaseService companyCaseService) {
        this.companyCaseService = companyCaseService;
    }

    @GetMapping("/summary")
    @PreAuthorize("hasRole('COMPANY_USER')")
    public ApiResponse<CompanyCaseSummaryResponse> summary() {
        return ApiResponse.ok(companyCaseService.summary());
    }
}
