package com.konselyavisa.organization.api;

import com.konselyavisa.common.api.ApiResponse;
import com.konselyavisa.organization.service.OrganizationSettingsWriteService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/organizations")
public class OrganizationSettingsController {

    private final OrganizationSettingsWriteService organizationSettingsWriteService;

    public OrganizationSettingsController(OrganizationSettingsWriteService organizationSettingsWriteService) {
        this.organizationSettingsWriteService = organizationSettingsWriteService;
    }

    @PatchMapping("/me/settings")
    @PreAuthorize("hasAnyRole('BUSINESS_ADMIN', 'PLATFORM_ADMIN')")
    public ApiResponse<OrganizationResponse> updateMySettings(@RequestBody UpdateOrganizationWhiteLabelRequest request) {
        return ApiResponse.ok(
                organizationSettingsWriteService.updateCurrent(request), "organization.settings_updated");
    }
}
