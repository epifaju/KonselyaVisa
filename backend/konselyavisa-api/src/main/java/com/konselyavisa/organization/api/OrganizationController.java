package com.konselyavisa.organization.api;

import com.konselyavisa.common.api.ApiResponse;
import com.konselyavisa.common.api.PageResponse;
import com.konselyavisa.organization.service.OrganizationService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/organizations")
public class OrganizationController {

    private final OrganizationService organizationService;

    public OrganizationController(OrganizationService organizationService) {
        this.organizationService = organizationService;
    }

    @GetMapping("/me")
    public ApiResponse<OrganizationResponse> me() {
        return ApiResponse.ok(organizationService.getCurrent());
    }

    @GetMapping("/{id}")
    public ApiResponse<OrganizationResponse> getById(@PathVariable UUID id) {
        return ApiResponse.ok(organizationService.getById(id));
    }

    @GetMapping
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public PageResponse<OrganizationResponse> list(@PageableDefault(size = 20) Pageable pageable) {
        return organizationService.list(pageable);
    }

    @PostMapping
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public ApiResponse<OrganizationResponse> create(@Valid @RequestBody CreateOrganizationRequest request) {
        return ApiResponse.ok(organizationService.create(request), "organization.created");
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN', 'BUSINESS_ADMIN')")
    public ApiResponse<OrganizationResponse> update(
            @PathVariable UUID id, @RequestBody UpdateOrganizationRequest request) {
        return ApiResponse.ok(organizationService.update(id, request), "organization.updated");
    }
}
