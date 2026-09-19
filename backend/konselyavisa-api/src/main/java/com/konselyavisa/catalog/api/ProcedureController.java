package com.konselyavisa.catalog.api;

import com.konselyavisa.catalog.domain.ProcedureCategory;
import com.konselyavisa.catalog.service.ProcedureService;
import com.konselyavisa.common.api.ApiResponse;
import com.konselyavisa.common.api.PageResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/procedures")
public class ProcedureController {

    private final ProcedureService procedureService;

    public ProcedureController(ProcedureService procedureService) {
        this.procedureService = procedureService;
    }

    @GetMapping
    public PageResponse<ProcedureResponse> search(
            @RequestParam(required = false) UUID originCountryId,
            @RequestParam(required = false) UUID destinationCountryId,
            @RequestParam(required = false) ProcedureCategory category,
            @PageableDefault(size = 20) Pageable pageable,
            Authentication authentication) {
        return procedureService.search(
                originCountryId, destinationCountryId, category, canManageCatalog(authentication), pageable);
    }

    @GetMapping("/{id}")
    public ApiResponse<ProcedureResponse> getById(@PathVariable UUID id, Authentication authentication) {
        return ApiResponse.ok(procedureService.getById(id, canManageCatalog(authentication)));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN', 'BUSINESS_ADMIN')")
    public ApiResponse<ProcedureResponse> create(@Valid @RequestBody CreateProcedureRequest request) {
        return ApiResponse.ok(procedureService.create(request), "catalog.procedure.created");
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN', 'BUSINESS_ADMIN')")
    public ApiResponse<ProcedureResponse> update(
            @PathVariable UUID id, @RequestBody UpdateProcedureRequest request) {
        return ApiResponse.ok(procedureService.update(id, request), "catalog.procedure.updated");
    }

    @PostMapping("/{id}/versions")
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN', 'BUSINESS_ADMIN')")
    public ApiResponse<ProcedureVersionResponse> createVersion(
            @PathVariable UUID id, @RequestBody(required = false) CreateProcedureVersionRequest request) {
        CreateProcedureVersionRequest body = request == null
                ? new CreateProcedureVersionRequest(null, null, null, null)
                : request;
        return ApiResponse.ok(procedureService.createVersion(id, body), "catalog.version.created");
    }

    @PatchMapping("/{id}/versions/{versionId}")
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN', 'BUSINESS_ADMIN')")
    public ApiResponse<ProcedureVersionResponse> updateVersion(
            @PathVariable UUID id,
            @PathVariable UUID versionId,
            @RequestBody UpdateProcedureVersionRequest request) {
        return ApiResponse.ok(procedureService.updateVersion(id, versionId, request), "catalog.version.updated");
    }

    @PostMapping("/{id}/versions/{versionId}/publish")
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN', 'BUSINESS_ADMIN')")
    public ApiResponse<ProcedureVersionResponse> publish(@PathVariable UUID id, @PathVariable UUID versionId) {
        return ApiResponse.ok(procedureService.publish(id, versionId), "catalog.version.published");
    }

    @PostMapping("/{id}/versions/{versionId}/archive")
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN', 'BUSINESS_ADMIN')")
    public ApiResponse<ProcedureVersionResponse> archive(@PathVariable UUID id, @PathVariable UUID versionId) {
        return ApiResponse.ok(procedureService.archive(id, versionId), "catalog.version.archived");
    }

    private static boolean canManageCatalog(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_PLATFORM_ADMIN".equals(authority.getAuthority())
                        || "ROLE_BUSINESS_ADMIN".equals(authority.getAuthority()));
    }
}
