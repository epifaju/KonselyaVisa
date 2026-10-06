package com.konselyavisa.guest.api;

import com.konselyavisa.common.api.ApiResponse;
import com.konselyavisa.guest.GuestEligibilityService;
import com.konselyavisa.guest.PublicHost;
import com.konselyavisa.organization.api.OrganizationBrandingResponse;
import com.konselyavisa.organization.service.OrganizationBrandingService;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/public")
public class PublicOrgController {

    private final GuestEligibilityService guestEligibilityService;
    private final OrganizationBrandingService organizationBrandingService;

    public PublicOrgController(
            GuestEligibilityService guestEligibilityService, OrganizationBrandingService organizationBrandingService) {
        this.guestEligibilityService = guestEligibilityService;
        this.organizationBrandingService = organizationBrandingService;
    }

    @GetMapping("/org")
    public ApiResponse<OrganizationBrandingResponse> org(
            @RequestParam(required = false) UUID organizationId,
            @RequestParam(required = false) String domain,
            @RequestHeader(value = "Host", required = false) String host) {
        guestEligibilityService.bindOrganization(organizationId, PublicHost.domainHint(domain, host));
        return ApiResponse.ok(organizationBrandingService.current(), "public.org");
    }
}
