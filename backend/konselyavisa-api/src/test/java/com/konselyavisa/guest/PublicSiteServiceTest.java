package com.konselyavisa.guest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.konselyavisa.catalog.domain.ProcedureCategory;
import com.konselyavisa.catalog.domain.ProcedureDefinition;
import com.konselyavisa.catalog.persistence.ProcedureDefinitionRepository;
import com.konselyavisa.catalog.service.ProcedureAccessGuard;
import com.konselyavisa.common.exception.BusinessException;
import com.konselyavisa.organization.OrganizationBrandingAssets;
import com.konselyavisa.organization.api.OrganizationResponse;
import com.konselyavisa.organization.domain.OrganizationStatus;
import com.konselyavisa.organization.service.OrganizationService;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

@ExtendWith(MockitoExtension.class)
class PublicSiteServiceTest {

    @Mock
    private GuestEligibilityService guestEligibilityService;

    @Mock
    private OrganizationService organizationService;

    @Mock
    private ProcedureDefinitionRepository procedureDefinitionRepository;

    @Mock
    private ProcedureAccessGuard procedureAccessGuard;

    @InjectMocks
    private PublicSiteService service;

    @Test
    void loadRejectsInactiveOrganization() {
        UUID orgId = UUID.randomUUID();
        when(organizationService.getCurrent()).thenReturn(orgResponse(orgId, OrganizationStatus.SUSPENDED, Map.of()));

        assertThatThrownBy(() -> service.load(orgId))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getMessageKey())
                .isEqualTo("error.organization.not_found");
        verify(guestEligibilityService).bindOrganization(orgId, null);
    }

    @Test
    void loadReturnsBrandingAndDedupedFormalities() {
        UUID orgId = UUID.randomUUID();
        Map<String, Object> settings = Map.of(
                "brandColor",
                "#1a4d8c",
                "domain",
                "visa.example",
                "contactEmail",
                "hello@example.com",
                "activeLanguages",
                List.of("fr", "pt"),
                "addressI18n",
                Map.of("fr", "Lisbonne"),
                OrganizationBrandingAssets.contentTypeKey(OrganizationBrandingAssets.LOGO),
                "image/png");
        when(organizationService.getCurrent()).thenReturn(orgResponse(orgId, OrganizationStatus.ACTIVE, settings));

        ProcedureDefinition visa = procedure(ProcedureCategory.VISA, Map.of("fr", "Visa tourisme"));
        ProcedureDefinition visa2 = procedure(ProcedureCategory.VISA, Map.of("fr", "Visa affaires"));
        ProcedureDefinition evisa = procedure(ProcedureCategory.EVISA, Map.of("fr", "eVisa"));
        ProcedureDefinition hidden = procedure(ProcedureCategory.INSURANCE, Map.of("fr", "Assurance"));
        when(procedureDefinitionRepository.search(isNull(), isNull(), isNull(), eq(true), eq(true), any()))
                .thenReturn(new PageImpl<>(List.of(visa, visa2, evisa, hidden), PageRequest.of(0, 100), 4));
        when(procedureAccessGuard.isOfferedToCitizens(visa)).thenReturn(true);
        when(procedureAccessGuard.isOfferedToCitizens(visa2)).thenReturn(true);
        when(procedureAccessGuard.isOfferedToCitizens(evisa)).thenReturn(true);
        when(procedureAccessGuard.isOfferedToCitizens(hidden)).thenReturn(false);

        PublicSiteResponse response = service.load(orgId, "visa.example");

        verify(guestEligibilityService).bindOrganization(orgId, "visa.example");
        assertThat(response.organizationId()).isEqualTo(orgId);
        assertThat(response.brandColor()).isEqualTo("#1A4D8C");
        assertThat(response.domain()).isEqualTo("visa.example");
        assertThat(response.contactEmail()).isEqualTo("hello@example.com");
        assertThat(response.activeLanguages()).containsExactly("fr", "pt");
        assertThat(response.addressI18n()).containsEntry("fr", "Lisbonne");
        assertThat(response.logoUrl())
                .isEqualTo(OrganizationBrandingAssets.publicPath(orgId, OrganizationBrandingAssets.LOGO));
        assertThat(response.faviconUrl()).isNull();
        assertThat(response.formalities())
                .containsExactly(
                        new PublicFormalityResponse("VISA", Map.of("fr", "Visa tourisme")),
                        new PublicFormalityResponse("EVISA", Map.of("fr", "eVisa")));
    }

    private static OrganizationResponse orgResponse(
            UUID id, OrganizationStatus status, Map<String, Object> settings) {
        return new OrganizationResponse(
                id, "DEMO", "demo", Map.of("fr", "Demo"), status, "fr", "EUR", settings);
    }

    private static ProcedureDefinition procedure(ProcedureCategory category, Map<String, String> nameI18n) {
        ProcedureDefinition definition = new ProcedureDefinition();
        definition.setId(UUID.randomUUID());
        definition.setCategory(category);
        definition.setNameI18n(nameI18n);
        definition.setActive(true);
        return definition;
    }
}
