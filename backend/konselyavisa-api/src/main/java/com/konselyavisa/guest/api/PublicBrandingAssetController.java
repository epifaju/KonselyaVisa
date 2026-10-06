package com.konselyavisa.guest.api;

import com.konselyavisa.guest.GuestEligibilityService;
import com.konselyavisa.guest.PublicHost;
import com.konselyavisa.organization.service.OrganizationBrandingAssetService;
import com.konselyavisa.organization.service.OrganizationBrandingAssetService.BrandingAsset;
import com.konselyavisa.tenancy.TenantContext;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/public/org/branding")
public class PublicBrandingAssetController {

    private final GuestEligibilityService guestEligibilityService;
    private final OrganizationBrandingAssetService brandingAssetService;

    public PublicBrandingAssetController(
            GuestEligibilityService guestEligibilityService, OrganizationBrandingAssetService brandingAssetService) {
        this.guestEligibilityService = guestEligibilityService;
        this.brandingAssetService = brandingAssetService;
    }

    @GetMapping("/{asset}")
    public ResponseEntity<byte[]> asset(
            @PathVariable String asset,
            @RequestParam(required = false) UUID organizationId,
            @RequestParam(required = false) String domain,
            @RequestHeader(value = "Host", required = false) String host) {
        guestEligibilityService.bindOrganization(organizationId, PublicHost.domainHint(domain, host));
        UUID resolved = TenantContext.getOrganizationId();
        BrandingAsset brandingAsset = brandingAssetService.load(resolved, asset);
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, CacheControl.maxAge(java.time.Duration.ofHours(1)).cachePublic().getHeaderValue())
                .contentType(MediaType.parseMediaType(brandingAsset.contentType()))
                .body(brandingAsset.bytes());
    }
}
