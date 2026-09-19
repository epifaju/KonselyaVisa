package com.konselyavisa.guest.api;

import com.konselyavisa.common.api.ApiResponse;
import com.konselyavisa.guest.PublicCaseLookupRequest;
import com.konselyavisa.guest.PublicCaseLookupService;
import com.konselyavisa.guest.PublicCaseStatusResponse;
import com.konselyavisa.guest.PublicSiteResponse;
import com.konselyavisa.guest.PublicSiteService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/public")
public class PublicSiteController {

    private final PublicSiteService publicSiteService;
    private final PublicCaseLookupService publicCaseLookupService;

    public PublicSiteController(
            PublicSiteService publicSiteService, PublicCaseLookupService publicCaseLookupService) {
        this.publicSiteService = publicSiteService;
        this.publicCaseLookupService = publicCaseLookupService;
    }

    @GetMapping("/site")
    public ApiResponse<PublicSiteResponse> site(@RequestParam(required = false) UUID organizationId) {
        return ApiResponse.ok(publicSiteService.load(organizationId), "public.site");
    }

    @PostMapping("/case-status")
    public ApiResponse<PublicCaseStatusResponse> caseStatus(@Valid @RequestBody PublicCaseLookupRequest request) {
        publicCaseLookupService.bindBeforeLookup(request.organizationId());
        return ApiResponse.ok(publicCaseLookupService.lookup(request), "public.case_status");
    }
}
