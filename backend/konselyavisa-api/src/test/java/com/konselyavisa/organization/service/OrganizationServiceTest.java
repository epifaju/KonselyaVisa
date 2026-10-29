package com.konselyavisa.organization.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OrganizationServiceTest {

    @Mock
    private OrganizationRepository organizationRepository;

    @Mock
    private OrganizationMapper organizationMapper;

    private OrganizationService service;
    private UUID orgId;

    @BeforeEach
    void setUp() {
        orgId = UUID.randomUUID();
        service = new OrganizationService(organizationRepository, organizationMapper);
        TenantContext.clear();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void getCurrentRequiresTenant() {
        assertThatThrownBy(() -> service.getCurrent())
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getMessageKey())
                .isEqualTo("error.organization.missing");
    }

    @Test
    void getByIdDeniesCrossTenantAccess() {
        TenantContext.setOrganizationId(orgId);

        assertThatThrownBy(() -> service.getById(UUID.randomUUID()))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getMessageKey())
                .isEqualTo("error.access.denied");
    }

    @Test
    void createRejectsDuplicateCode() {
        CreateOrganizationRequest request = new CreateOrganizationRequest(
                "DEMO", "demo", Map.of("fr", "Demo"), "fr", "EUR", Map.of(), null);
        when(organizationRepository.existsByCodeIgnoreCase("DEMO")).thenReturn(true);

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getMessageKey())
                .isEqualTo("error.organization.code_taken");
        verify(organizationRepository, never()).save(any());
    }

    @Test
    void createPersistsNormalizedCodeAndDefaults() {
        CreateOrganizationRequest request = new CreateOrganizationRequest(
                " demo ", "demo-org", Map.of("fr", "Demo"), null, null, Map.of("paymentProvider", "mock"), null);
        when(organizationRepository.existsByCodeIgnoreCase(" demo ")).thenReturn(false);
        when(organizationRepository.existsBySlugIgnoreCase("demo-org")).thenReturn(false);
        when(organizationRepository.save(any(Organization.class))).thenAnswer(inv -> inv.getArgument(0));
        OrganizationResponse mapped = org.mockito.Mockito.mock(OrganizationResponse.class);
        when(organizationMapper.toResponse(any(Organization.class))).thenReturn(mapped);

        assertThat(service.create(request)).isSameAs(mapped);
        ArgumentCaptor<Organization> captor = ArgumentCaptor.forClass(Organization.class);
        verify(organizationRepository).save(captor.capture());
        assertThat(captor.getValue().getCode()).isEqualTo("DEMO");
        assertThat(captor.getValue().getDefaultLocale()).isEqualTo("fr");
        assertThat(captor.getValue().getDefaultCurrency()).isEqualTo("EUR");
        assertThat(captor.getValue().getStatus()).isEqualTo(OrganizationStatus.ACTIVE);
        assertThat(captor.getValue().getSettings().getSettings()).containsEntry("paymentProvider", "mock");
    }

    @Test
    void updateForbidsStatusChangeForNonAdmin() {
        TenantContext.setOrganizationId(orgId);

        assertThatThrownBy(() -> service.update(
                        orgId,
                        new UpdateOrganizationRequest(null, null, null, null, OrganizationStatus.SUSPENDED)))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getMessageKey())
                .isEqualTo("error.organization.status_forbidden");
        verify(organizationRepository, never()).findById(any());
    }

    @Test
    void updateAllowsPlatformAdminToChangeStatus() {
        TenantContext.setPlatformAdmin(true);
        Organization organization = organization();
        when(organizationRepository.findById(orgId)).thenReturn(Optional.of(organization));
        OrganizationResponse mapped = org.mockito.Mockito.mock(OrganizationResponse.class);
        when(organizationMapper.toResponse(organization)).thenReturn(mapped);

        assertThat(service.update(
                        orgId,
                        new UpdateOrganizationRequest(
                                Map.of("fr", "Nouveau"), null, "usd", null, OrganizationStatus.SUSPENDED)))
                .isSameAs(mapped);
        assertThat(organization.getStatus()).isEqualTo(OrganizationStatus.SUSPENDED);
        assertThat(organization.getDefaultCurrency()).isEqualTo("USD");
        assertThat(organization.getNameI18n()).containsEntry("fr", "Nouveau");
    }

    @Test
    void createRejectsDuplicateSlugAndTrimsExplicitLocale() {
        CreateOrganizationRequest duplicate = new CreateOrganizationRequest(
                "ACME", "acme", Map.of("fr", "Acme"), "fr", "EUR", null, OrganizationStatus.SUSPENDED);
        when(organizationRepository.existsByCodeIgnoreCase("ACME")).thenReturn(false);
        when(organizationRepository.existsBySlugIgnoreCase("acme")).thenReturn(true);

        assertThatThrownBy(() -> service.create(duplicate))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getMessageKey())
                .isEqualTo("error.organization.slug_taken");

        CreateOrganizationRequest request = new CreateOrganizationRequest(
                "ACME", "acme", Map.of("fr", "Acme"), "  fr  ", "   ", null, OrganizationStatus.SUSPENDED);
        when(organizationRepository.existsBySlugIgnoreCase("acme")).thenReturn(false);
        when(organizationRepository.save(any(Organization.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(organizationMapper.toResponse(any(Organization.class)))
                .thenReturn(org.mockito.Mockito.mock(OrganizationResponse.class));

        service.create(request);

        ArgumentCaptor<Organization> captor = ArgumentCaptor.forClass(Organization.class);
        verify(organizationRepository).save(captor.capture());
        assertThat(captor.getValue().getDefaultLocale()).isEqualTo("fr");
        assertThat(captor.getValue().getDefaultCurrency()).isEqualTo("EUR");
        assertThat(captor.getValue().getStatus()).isEqualTo(OrganizationStatus.SUSPENDED);
        assertThat(captor.getValue().getSettings().getSettings()).isEmpty();
    }

    @Test
    void updateIgnoresBlankFieldsAndMergesSettings() {
        TenantContext.setOrganizationId(orgId);
        Organization organization = organization();
        when(organizationRepository.findById(orgId)).thenReturn(Optional.of(organization));
        when(organizationMapper.toResponse(organization))
                .thenReturn(org.mockito.Mockito.mock(OrganizationResponse.class));

        service.update(orgId, new UpdateOrganizationRequest(Map.of(), "  ", " ", Map.of("theme", "green"), null));

        assertThat(organization.getNameI18n()).containsEntry("fr", "Demo");
        assertThat(organization.getDefaultLocale()).isEqualTo("fr");
        assertThat(organization.getDefaultCurrency()).isEqualTo("EUR");
        assertThat(organization.getSettings().getSettings()).containsEntry("theme", "green");
    }

    @Test
    void updateRejectsUnknownOrganization() {
        TenantContext.setPlatformAdmin(true);
        when(organizationRepository.findById(orgId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(orgId, new UpdateOrganizationRequest(null, "pt", null, null, null)))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getMessageKey())
                .isEqualTo("error.organization.not_found");
    }

    @Test
    void listMapsTheRepositoryPage() {
        Organization organization = organization();
        OrganizationResponse mapped = org.mockito.Mockito.mock(OrganizationResponse.class);
        when(organizationRepository.findAll(PageRequest.of(0, 5)))
                .thenReturn(new PageImpl<>(java.util.List.of(organization)));
        when(organizationMapper.toResponse(organization)).thenReturn(mapped);

        PageResponse<OrganizationResponse> page = service.list(PageRequest.of(0, 5));

        assertThat(page.content()).containsExactly(mapped);
    }

    private Organization organization() {
        Organization organization = new Organization();
        organization.setId(orgId);
        organization.setCode("DEMO");
        organization.setSlug("demo");
        organization.setNameI18n(Map.of("fr", "Demo"));
        organization.setDefaultLocale("fr");
        organization.setDefaultCurrency("EUR");
        organization.setStatus(OrganizationStatus.ACTIVE);
        OrganizationSettings settings = new OrganizationSettings();
        settings.setOrganization(organization);
        settings.setSettings(new java.util.HashMap<>());
        organization.setSettings(settings);
        return organization;
    }
}
