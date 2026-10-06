package com.konselyavisa.privacy.api;

import com.konselyavisa.common.api.ApiResponse;
import com.konselyavisa.common.exception.BusinessException;
import com.konselyavisa.privacy.OrganizationDpaService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/organizations/me/dpa")
public class OrganizationDpaController {

    private final OrganizationDpaService organizationDpaService;

    public OrganizationDpaController(OrganizationDpaService organizationDpaService) {
        this.organizationDpaService = organizationDpaService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('BUSINESS_ADMIN', 'PLATFORM_ADMIN', 'SUPERVISOR', 'AGENT')")
    public ApiResponse<OrganizationDpaResponse> current() {
        OrganizationDpaResponse response = organizationDpaService
                .findActiveForCurrentOrg()
                .orElseThrow(() -> BusinessException.notFound("error.privacy.dpa_missing"));
        return ApiResponse.ok(response, "privacy.dpa_ready");
    }

    @PostMapping("/accept")
    @PreAuthorize("hasAnyRole('BUSINESS_ADMIN', 'PLATFORM_ADMIN')")
    public ApiResponse<OrganizationDpaResponse> accept(@Valid @RequestBody AcceptOrganizationDpaRequest request) {
        return ApiResponse.ok(organizationDpaService.accept(request), "privacy.dpa_accepted");
    }
}
