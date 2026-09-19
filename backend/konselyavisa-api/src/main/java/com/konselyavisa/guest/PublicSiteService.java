package com.konselyavisa.guest;

import com.konselyavisa.catalog.domain.ProcedureDefinition;
import com.konselyavisa.catalog.persistence.ProcedureDefinitionRepository;
import com.konselyavisa.catalog.service.ProcedureAccessGuard;
import com.konselyavisa.common.exception.BusinessException;
import com.konselyavisa.organization.api.OrganizationResponse;
import com.konselyavisa.organization.domain.OrganizationStatus;
import com.konselyavisa.organization.service.OrganizationService;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

@Service
public class PublicSiteService {

    private final GuestEligibilityService guestEligibilityService;
    private final OrganizationService organizationService;
    private final ProcedureDefinitionRepository procedureDefinitionRepository;
    private final ProcedureAccessGuard procedureAccessGuard;

    public PublicSiteService(
            GuestEligibilityService guestEligibilityService,
            OrganizationService organizationService,
            ProcedureDefinitionRepository procedureDefinitionRepository,
            ProcedureAccessGuard procedureAccessGuard) {
        this.guestEligibilityService = guestEligibilityService;
        this.organizationService = organizationService;
        this.procedureDefinitionRepository = procedureDefinitionRepository;
        this.procedureAccessGuard = procedureAccessGuard;
    }

    public PublicSiteResponse load(UUID organizationId) {
        guestEligibilityService.bindOrganization(organizationId);
        OrganizationResponse organization = organizationService.getCurrent();
        if (organization.status() != OrganizationStatus.ACTIVE) {
            throw BusinessException.notFound("error.organization.not_found");
        }
        Map<String, Object> settings = organization.settings() == null ? Map.of() : organization.settings();
        Set<String> categories = new LinkedHashSet<>();
        List<PublicFormalityResponse> formalities = new ArrayList<>();
        for (ProcedureDefinition procedure : procedureDefinitionRepository
                .search(null, null, null, true, true, PageRequest.of(0, 100))
                .getContent()) {
            if (!procedureAccessGuard.isOfferedToCitizens(procedure)) {
                continue;
            }
            String category = procedure.getCategory().name();
            if (categories.add(category)) {
                formalities.add(new PublicFormalityResponse(category, Map.copyOf(procedure.getNameI18n())));
            }
        }
        return new PublicSiteResponse(
                organization.id(),
                organization.nameI18n(),
                organization.defaultLocale(),
                PublicSiteSettings.languages(settings, organization.defaultLocale()),
                PublicSiteSettings.i18n(settings, "addressI18n"),
                PublicSiteSettings.i18n(settings, "openingHoursI18n"),
                PublicSiteSettings.text(settings, "contactEmail"),
                PublicSiteSettings.text(settings, "contactPhone"),
                List.copyOf(formalities));
    }
}
