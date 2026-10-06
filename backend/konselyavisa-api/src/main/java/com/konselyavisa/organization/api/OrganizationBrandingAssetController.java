package com.konselyavisa.organization.api;

import com.konselyavisa.common.api.ApiResponse;
import com.konselyavisa.organization.service.OrganizationBrandingAssetService;
import com.konselyavisa.organization.service.OrganizationBrandingService;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/organizations/me/branding")
public class OrganizationBrandingAssetController {

    private final OrganizationBrandingAssetService brandingAssetService;
    private final OrganizationBrandingService organizationBrandingService;

    public OrganizationBrandingAssetController(
            OrganizationBrandingAssetService brandingAssetService,
            OrganizationBrandingService organizationBrandingService) {
        this.brandingAssetService = brandingAssetService;
        this.organizationBrandingService = organizationBrandingService;
    }

    @PostMapping(path = "/{asset}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('BUSINESS_ADMIN', 'PLATFORM_ADMIN')")
    public ApiResponse<OrganizationBrandingResponse> upload(
            @PathVariable String asset, @RequestPart("file") MultipartFile file) {
        brandingAssetService.upload(asset, file);
        return ApiResponse.ok(organizationBrandingService.current(), "organization.branding_uploaded");
    }

    @DeleteMapping("/{asset}")
    @PreAuthorize("hasAnyRole('BUSINESS_ADMIN', 'PLATFORM_ADMIN')")
    public ApiResponse<OrganizationBrandingResponse> delete(@PathVariable String asset) {
        brandingAssetService.delete(asset);
        return ApiResponse.ok(organizationBrandingService.current(), "organization.branding_cleared");
    }
}
