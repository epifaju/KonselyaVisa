package com.konselyavisa.organization.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.konselyavisa.common.exception.BusinessException;
import com.konselyavisa.organization.api.OrganizationMapper;
import com.konselyavisa.organization.api.OrganizationResponse;
import com.konselyavisa.organization.api.UpdateOrganizationWhiteLabelRequest;
import com.konselyavisa.organization.domain.Organization;
import com.konselyavisa.organization.domain.OrganizationSettings;
import com.konselyavisa.organization.domain.OrganizationStatus;
import com.konselyavisa.organization.persistence.OrganizationRepository;
import com.konselyavisa.tenancy.TenantContext;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OrganizationSettingsWriteServiceTest {

    @Mock
    private OrganizationRepository organizationRepository;

    @Mock
    private OrganizationMapper organizationMapper;

    private OrganizationSettingsWriteService service;
    private UUID orgId;

    @BeforeEach
    void setUp() {
        orgId = UUID.randomUUID();
        service = new OrganizationSettingsWriteService(organizationRepository, organizationMapper);
        TenantContext.clear();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void updateCurrentForbiddenWhenTenantMissing() {
        assertThatThrownBy(() -> service.updateCurrent(new UpdateOrganizationWhiteLabelRequest(null, null, null)))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getMessageKey())
                .isEqualTo("error.organization.missing");
    }

    @Test
    void updateCurrentNotFoundWhenOrgAbsent() {
        TenantContext.setOrganizationId(orgId);
        when(organizationRepository.findById(orgId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateCurrent(new UpdateOrganizationWhiteLabelRequest("#fff", null, null)))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getMessageKey())
                .isEqualTo("error.organization.not_found");
    }

    @Test
    void updateCurrentClearsBrandColorWhenBlank() {
        TenantContext.setOrganizationId(orgId);
        Organization organization = organization(Map.of("brandColor", "#112233"));
        when(organizationRepository.findById(orgId)).thenReturn(Optional.of(organization));
        OrganizationResponse mapped = org.mockito.Mockito.mock(OrganizationResponse.class);
        when(organizationMapper.toResponse(organization)).thenReturn(mapped);

        assertThat(service.updateCurrent(new UpdateOrganizationWhiteLabelRequest("  ", null, null))).isSameAs(mapped);
        assertThat(organization.getSettings().getSettings()).doesNotContainKey("brandColor");
    }

    @Test
    void updateCurrentRejectsInvalidDomain() {
        TenantContext.setOrganizationId(orgId);
        when(organizationRepository.findById(orgId)).thenReturn(Optional.of(organization(new HashMap<>())));

        assertThatThrownBy(() ->
                        service.updateCurrent(new UpdateOrganizationWhiteLabelRequest(null, "not a domain", null)))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getMessageKey())
                .isEqualTo("error.organization.domain_invalid");
    }

    @Test
    void updateCurrentRejectsUnknownPaymentProvider() {
        TenantContext.setOrganizationId(orgId);
        when(organizationRepository.findById(orgId)).thenReturn(Optional.of(organization(new HashMap<>())));

        assertThatThrownBy(() ->
                        service.updateCurrent(new UpdateOrganizationWhiteLabelRequest(null, null, "paypal")))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getMessageKey())
                .isEqualTo("error.organization.payment_provider_invalid");
    }

    @Test
    void updateCurrentNormalizesValidWhiteLabelFields() {
        TenantContext.setOrganizationId(orgId);
        Organization organization = organization(new HashMap<>());
        when(organizationRepository.findById(orgId)).thenReturn(Optional.of(organization));
        OrganizationResponse mapped = org.mockito.Mockito.mock(OrganizationResponse.class);
        when(organizationMapper.toResponse(organization)).thenReturn(mapped);

        assertThat(service.updateCurrent(
                        new UpdateOrganizationWhiteLabelRequest("#1a4d8c", "Visa.Example.com", " stripe ")))
                .isSameAs(mapped);
        Map<String, Object> settings = organization.getSettings().getSettings();
        assertThat(settings)
                .containsEntry("brandColor", "#1A4D8C")
                .containsEntry("domain", "visa.example.com")
                .containsEntry("paymentProvider", "STRIPE");
        verify(organizationMapper).toResponse(organization);
    }

    private Organization organization(Map<String, Object> settingsMap) {
        Organization organization = new Organization();
        organization.setId(orgId);
        organization.setStatus(OrganizationStatus.ACTIVE);
        OrganizationSettings settings = new OrganizationSettings();
        settings.setOrganization(organization);
        settings.setSettings(settingsMap instanceof HashMap ? settingsMap : new HashMap<>(settingsMap));
        organization.setSettings(settings);
        return organization;
    }
}
