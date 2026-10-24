package com.konselyavisa.organization.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.konselyavisa.common.exception.BusinessException;
import com.konselyavisa.document.storage.ObjectStorage;
import com.konselyavisa.organization.OrganizationBrandingAssets;
import com.konselyavisa.organization.domain.Organization;
import com.konselyavisa.organization.domain.OrganizationSettings;
import com.konselyavisa.organization.persistence.OrganizationRepository;
import com.konselyavisa.tenancy.TenantContext;
import java.util.HashMap;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

@ExtendWith(MockitoExtension.class)
class OrganizationBrandingAssetServiceTest {

    @Mock
    private OrganizationRepository organizationRepository;

    @Mock
    private ObjectStorage objectStorage;

    private OrganizationBrandingAssetService service;
    private UUID orgId;

    @BeforeEach
    void setUp() {
        orgId = UUID.randomUUID();
        service = new OrganizationBrandingAssetService(organizationRepository, objectStorage);
        TenantContext.clear();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void uploadRejectsInvalidAssetAndMissingTenant() {
        assertThatThrownBy(() -> service.upload("banner", tinyPng()))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getMessageKey())
                .isEqualTo("error.organization.branding_asset_invalid");

        assertThatThrownBy(() -> service.upload(OrganizationBrandingAssets.LOGO, tinyPng()))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getMessageKey())
                .isEqualTo("error.organization.missing");
    }

    @Test
    void uploadRejectsEmptyFileInvalidTypeAndOversizedLogo() {
        TenantContext.setOrganizationId(orgId);

        assertThatThrownBy(() -> service.upload(OrganizationBrandingAssets.LOGO, emptyFile()))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getMessageKey())
                .isEqualTo("error.organization.branding_file_required");

        assertThatThrownBy(() -> service.upload(
                        OrganizationBrandingAssets.LOGO,
                        new MockMultipartFile("f", "x.gif", "image/gif", new byte[] {1})))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getMessageKey())
                .isEqualTo("error.organization.branding_type_invalid");

        byte[] huge = new byte[(int) OrganizationBrandingAssets.LOGO_MAX_BYTES + 1];
        assertThatThrownBy(() -> service.upload(
                        OrganizationBrandingAssets.LOGO,
                        new MockMultipartFile("f", "x.png", "image/png", huge)))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getMessageKey())
                .isEqualTo("error.organization.branding_too_large");
        verify(objectStorage, never()).put(any(), any(), any());
    }

    @Test
    void uploadStoresObjectAndUpdatesSettings() throws Exception {
        TenantContext.setOrganizationId(orgId);
        Organization organization = organization();
        when(organizationRepository.findById(orgId)).thenReturn(Optional.of(organization));
        MockMultipartFile file = tinyPng();

        service.upload(OrganizationBrandingAssets.LOGO, file);

        String key = OrganizationBrandingAssets.storageKey(orgId, OrganizationBrandingAssets.LOGO);
        verify(objectStorage).put(eq(key), eq(file.getBytes()), eq("image/png"));
        assertThat(organization.getSettings().getSettings())
                .containsEntry(OrganizationBrandingAssets.contentTypeKey(OrganizationBrandingAssets.LOGO), "image/png");
    }

    @Test
    void deleteRemovesObjectAndSettingsKey() {
        TenantContext.setOrganizationId(orgId);
        Organization organization = organization();
        organization
                .getSettings()
                .getSettings()
                .put(OrganizationBrandingAssets.contentTypeKey(OrganizationBrandingAssets.FAVICON), "image/x-icon");
        when(organizationRepository.findById(orgId)).thenReturn(Optional.of(organization));

        service.delete(OrganizationBrandingAssets.FAVICON);

        verify(objectStorage)
                .delete(OrganizationBrandingAssets.storageKey(orgId, OrganizationBrandingAssets.FAVICON));
        assertThat(organization.getSettings().getSettings())
                .doesNotContainKey(OrganizationBrandingAssets.contentTypeKey(OrganizationBrandingAssets.FAVICON));
    }

    @Test
    void loadNotFoundWhenSettingsOrObjectMissing() {
        Organization organization = organization();
        when(organizationRepository.findById(orgId)).thenReturn(Optional.of(organization));

        assertThatThrownBy(() -> service.load(orgId, OrganizationBrandingAssets.LOGO))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getMessageKey())
                .isEqualTo("error.organization.branding_missing");

        organization
                .getSettings()
                .getSettings()
                .put(OrganizationBrandingAssets.contentTypeKey(OrganizationBrandingAssets.LOGO), "image/png");
        String key = OrganizationBrandingAssets.storageKey(orgId, OrganizationBrandingAssets.LOGO);
        when(objectStorage.exists(key)).thenReturn(false);

        assertThatThrownBy(() -> service.load(orgId, OrganizationBrandingAssets.LOGO))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getMessageKey())
                .isEqualTo("error.organization.branding_missing");
    }

    @Test
    void loadReturnsBytesAndContentType() {
        Organization organization = organization();
        organization
                .getSettings()
                .getSettings()
                .put(OrganizationBrandingAssets.contentTypeKey(OrganizationBrandingAssets.LOGO), "image/png");
        when(organizationRepository.findById(orgId)).thenReturn(Optional.of(organization));
        String key = OrganizationBrandingAssets.storageKey(orgId, OrganizationBrandingAssets.LOGO);
        byte[] bytes = new byte[] {1, 2, 3};
        when(objectStorage.exists(key)).thenReturn(true);
        when(objectStorage.get(key)).thenReturn(bytes);

        OrganizationBrandingAssetService.BrandingAsset asset =
                service.load(orgId, OrganizationBrandingAssets.LOGO);

        assertThat(asset.bytes()).isEqualTo(bytes);
        assertThat(asset.contentType()).isEqualTo("image/png");
    }

    private Organization organization() {
        Organization organization = new Organization();
        organization.setId(orgId);
        OrganizationSettings settings = new OrganizationSettings();
        settings.setOrganization(organization);
        settings.setSettings(new HashMap<>());
        organization.setSettings(settings);
        return organization;
    }

    private static MockMultipartFile tinyPng() {
        return new MockMultipartFile("file", "logo.png", "image/png", new byte[] {1, 2, 3, 4});
    }

    private static MockMultipartFile emptyFile() {
        return new MockMultipartFile("file", "logo.png", "image/png", new byte[0]);
    }
}
