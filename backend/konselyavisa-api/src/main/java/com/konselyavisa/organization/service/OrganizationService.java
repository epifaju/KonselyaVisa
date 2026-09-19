package com.konselyavisa.organization.service;

import com.konselyavisa.common.api.PageResponse;
import com.konselyavisa.common.exception.BusinessException;
import com.konselyavisa.organization.api.CreateOrganizationRequest;
import com.konselyavisa.organization.api.OrganizationMapper;
import com.konselyavisa.organization.api.OrganizationResponse;
import com.konselyavisa.organization.api.UpdateOrganizationRequest;
import com.konselyavisa.organization.domain.Organization;
import com.konselyavisa.organization.domain.OrganizationSettings;
import com.konselyavisa.organization.domain.OrganizationStatus;
import com.konselyavisa.organization.persistence.OrganizationRepository;
import com.konselyavisa.tenancy.TenantContext;
import java.util.HashMap;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrganizationService {

    private final OrganizationRepository organizationRepository;
    private final OrganizationMapper organizationMapper;

    public OrganizationService(OrganizationRepository organizationRepository, OrganizationMapper organizationMapper) {
        this.organizationRepository = organizationRepository;
        this.organizationMapper = organizationMapper;
    }

    @Transactional(readOnly = true)
    public OrganizationResponse getCurrent() {
        UUID organizationId = TenantContext.getOrganizationId();
        if (organizationId == null) {
            throw BusinessException.forbidden("error.organization.missing");
        }
        return organizationMapper.toResponse(getVisible(organizationId));
    }

    @Transactional(readOnly = true)
    public OrganizationResponse getById(UUID id) {
        assertCanAccess(id);
        return organizationMapper.toResponse(getVisible(id));
    }

    @Transactional(readOnly = true)
    public PageResponse<OrganizationResponse> list(Pageable pageable) {
        return PageResponse.from(organizationRepository.findAll(pageable).map(organizationMapper::toResponse));
    }

    @Transactional
    public OrganizationResponse create(CreateOrganizationRequest request) {
        if (organizationRepository.existsByCodeIgnoreCase(request.code())) {
            throw BusinessException.conflict("error.organization.code_taken");
        }
        if (organizationRepository.existsBySlugIgnoreCase(request.slug())) {
            throw BusinessException.conflict("error.organization.slug_taken");
        }
        Organization organization = new Organization();
        organization.setCode(request.code().trim().toUpperCase());
        organization.setSlug(request.slug().trim());
        organization.setNameI18n(new HashMap<>(request.nameI18n()));
        organization.setDefaultLocale(defaultString(request.defaultLocale(), "fr"));
        organization.setDefaultCurrency(defaultString(request.defaultCurrency(), "EUR").toUpperCase());
        organization.setStatus(request.status() == null ? OrganizationStatus.ACTIVE : request.status());

        OrganizationSettings settings = new OrganizationSettings();
        settings.setOrganization(organization);
        if (request.settings() != null) {
            settings.setSettings(new HashMap<>(request.settings()));
        }
        organization.setSettings(settings);
        return organizationMapper.toResponse(organizationRepository.save(organization));
    }

    @Transactional
    public OrganizationResponse update(UUID id, UpdateOrganizationRequest request) {
        assertCanAccess(id);
        if (!TenantContext.isPlatformAdmin() && request.status() != null) {
            throw BusinessException.forbidden("error.organization.status_forbidden");
        }
        Organization organization = getVisible(id);
        if (request.nameI18n() != null && !request.nameI18n().isEmpty()) {
            organization.setNameI18n(new HashMap<>(request.nameI18n()));
        }
        if (request.defaultLocale() != null && !request.defaultLocale().isBlank()) {
            organization.setDefaultLocale(request.defaultLocale());
        }
        if (request.defaultCurrency() != null && !request.defaultCurrency().isBlank()) {
            organization.setDefaultCurrency(request.defaultCurrency().toUpperCase());
        }
        if (request.status() != null) {
            organization.setStatus(request.status());
        }
        if (request.settings() != null) {
            organization.getSettings().getSettings().putAll(request.settings());
        }
        return organizationMapper.toResponse(organization);
    }

    private Organization getVisible(UUID id) {
        return organizationRepository
                .findById(id)
                .orElseThrow(() -> BusinessException.notFound("error.organization.not_found"));
    }

    private void assertCanAccess(UUID id) {
        if (TenantContext.isPlatformAdmin()) {
            return;
        }
        UUID current = TenantContext.getOrganizationId();
        if (current == null || !current.equals(id)) {
            throw BusinessException.forbidden("error.access.denied");
        }
    }

    private static String defaultString(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value.trim();
    }
}
