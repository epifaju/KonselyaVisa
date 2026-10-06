package com.konselyavisa.organization.service;

import com.konselyavisa.common.exception.BusinessException;
import com.konselyavisa.document.storage.ObjectStorage;
import com.konselyavisa.organization.OrganizationBrandingAssets;
import com.konselyavisa.organization.domain.Organization;
import com.konselyavisa.organization.persistence.OrganizationRepository;
import com.konselyavisa.tenancy.TenantContext;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class OrganizationBrandingAssetService {

    public record BrandingAsset(byte[] bytes, String contentType) {}

    private final OrganizationRepository organizationRepository;
    private final ObjectStorage objectStorage;

    public OrganizationBrandingAssetService(
            OrganizationRepository organizationRepository, ObjectStorage objectStorage) {
        this.organizationRepository = organizationRepository;
        this.objectStorage = objectStorage;
    }

    @Transactional
    public void upload(String asset, MultipartFile file) {
        requireAsset(asset);
        UUID organizationId = requireOrganization();
        if (file == null || file.isEmpty()) {
            throw BusinessException.badRequest("error.organization.branding_file_required");
        }
        String contentType = file.getContentType() == null ? "" : file.getContentType().toLowerCase(Locale.ROOT);
        if (!OrganizationBrandingAssets.isAllowedContentType(contentType)) {
            throw BusinessException.badRequest("error.organization.branding_type_invalid");
        }
        if (file.getSize() > OrganizationBrandingAssets.maxBytes(asset)) {
            throw BusinessException.badRequest("error.organization.branding_too_large");
        }
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (Exception ex) {
            throw BusinessException.badRequest("error.organization.branding_file_required");
        }
        Organization organization = requireOrg(organizationId);
        String key = OrganizationBrandingAssets.storageKey(organizationId, asset);
        objectStorage.put(key, bytes, contentType);
        organization.getSettings().getSettings().put(OrganizationBrandingAssets.contentTypeKey(asset), contentType);
    }

    @Transactional
    public void delete(String asset) {
        requireAsset(asset);
        UUID organizationId = requireOrganization();
        Organization organization = requireOrg(organizationId);
        String key = OrganizationBrandingAssets.storageKey(organizationId, asset);
        objectStorage.delete(key);
        organization.getSettings().getSettings().remove(OrganizationBrandingAssets.contentTypeKey(asset));
    }

    @Transactional(readOnly = true)
    public BrandingAsset load(UUID organizationId, String asset) {
        requireAsset(asset);
        Organization organization = requireOrg(organizationId);
        Map<String, Object> settings = organization.getSettings().getSettings();
        String contentType = OrganizationBrandingAssets.contentType(settings, asset);
        if (contentType == null) {
            throw BusinessException.notFound("error.organization.branding_missing");
        }
        String key = OrganizationBrandingAssets.storageKey(organizationId, asset);
        if (!objectStorage.exists(key)) {
            throw BusinessException.notFound("error.organization.branding_missing");
        }
        return new BrandingAsset(objectStorage.get(key), contentType);
    }

    private Organization requireOrg(UUID organizationId) {
        return organizationRepository
                .findById(organizationId)
                .orElseThrow(() -> BusinessException.notFound("error.organization.not_found"));
    }

    private static void requireAsset(String asset) {
        if (!OrganizationBrandingAssets.LOGO.equals(asset) && !OrganizationBrandingAssets.FAVICON.equals(asset)) {
            throw BusinessException.badRequest("error.organization.branding_asset_invalid");
        }
    }

    private static UUID requireOrganization() {
        UUID organizationId = TenantContext.getOrganizationId();
        if (organizationId == null) {
            throw BusinessException.forbidden("error.organization.missing");
        }
        return organizationId;
    }
}
