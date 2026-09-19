package com.konselyavisa.privacy.api;

import com.konselyavisa.common.api.ApiResponse;
import com.konselyavisa.privacy.PersonalDataRightsService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/me")
public class MePrivacyController {

    private final PersonalDataRightsService personalDataRightsService;

    public MePrivacyController(PersonalDataRightsService personalDataRightsService) {
        this.personalDataRightsService = personalDataRightsService;
    }

    @GetMapping("/data-export")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<PersonalDataExportResponse> exportMine() {
        return ApiResponse.ok(personalDataRightsService.exportMine(), "privacy.export_ready");
    }

    @PostMapping("/data-deletion-request")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<DataDeletionRequestResponse> requestDeletion() {
        return ApiResponse.ok(personalDataRightsService.requestDeletion(), "privacy.deletion_requested");
    }
}
