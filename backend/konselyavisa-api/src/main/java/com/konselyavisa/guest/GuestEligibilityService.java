package com.konselyavisa.guest;

import com.konselyavisa.catalog.domain.ProcedureDefinition;
import com.konselyavisa.catalog.domain.ProcedureVersion;
import com.konselyavisa.catalog.domain.ProcedureVersionStatus;
import com.konselyavisa.catalog.persistence.ProcedureDefinitionRepository;
import com.konselyavisa.catalog.persistence.ProcedureVersionRepository;
import com.konselyavisa.catalog.service.ProcedureAccessGuard;
import com.konselyavisa.common.exception.BusinessException;
import com.konselyavisa.dossier.ApplicantFactsGuard;
import com.konselyavisa.eligibility.JsonLogicEligibilityEvaluator;
import com.konselyavisa.organization.persistence.OrganizationRepository;
import com.konselyavisa.tenancy.TenantContext;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class GuestEligibilityService {

    private final GuestProperties guestProperties;
    private final GuestEligibilityTicketStore ticketStore;
    private final OrganizationRepository organizationRepository;
    private final ProcedureDefinitionRepository procedureDefinitionRepository;
    private final ProcedureVersionRepository procedureVersionRepository;
    private final ProcedureAccessGuard procedureAccessGuard;
    private final JsonLogicEligibilityEvaluator eligibilityEvaluator;

    public GuestEligibilityService(
            GuestProperties guestProperties,
            GuestEligibilityTicketStore ticketStore,
            OrganizationRepository organizationRepository,
            ProcedureDefinitionRepository procedureDefinitionRepository,
            ProcedureVersionRepository procedureVersionRepository,
            ProcedureAccessGuard procedureAccessGuard,
            JsonLogicEligibilityEvaluator eligibilityEvaluator) {
        this.guestProperties = guestProperties;
        this.ticketStore = ticketStore;
        this.organizationRepository = organizationRepository;
        this.procedureDefinitionRepository = procedureDefinitionRepository;
        this.procedureVersionRepository = procedureVersionRepository;
        this.procedureAccessGuard = procedureAccessGuard;
        this.eligibilityEvaluator = eligibilityEvaluator;
    }

    public UUID bindOrganization(UUID requestedOrganizationId) {
        UUID organizationId = requestedOrganizationId == null
                ? guestProperties.getDefaultOrganizationId()
                : requestedOrganizationId;
        TenantContext.setOrganizationId(organizationId);
        if (!organizationRepository.existsById(organizationId)) {
            throw BusinessException.notFound("error.organization.not_found");
        }
        return organizationId;
    }

    public GuestEligibilityEvaluateResponse evaluate(GuestEligibilityEvaluateRequest request) {
        UUID organizationId = bindOrganization(request.organizationId());
        ProcedureDefinition definition = procedureDefinitionRepository
                .findById(request.procedureDefinitionId())
                .orElseThrow(() -> BusinessException.notFound("error.catalog.procedure_not_found"));
        if (!definition.isActive()) {
            throw BusinessException.notFound("error.catalog.procedure_not_found");
        }
        procedureAccessGuard.assertOfferedToCitizens(definition);
        ProcedureVersion published = procedureVersionRepository
                .findByProcedureDefinitionIdAndStatus(definition.getId(), ProcedureVersionStatus.PUBLISHED)
                .orElseThrow(() -> BusinessException.badRequest("error.case.procedure_not_published"));
        Map<String, Object> facts = ApplicantFactsGuard.sanitize(request.facts());
        boolean eligible = eligibilityEvaluator.evaluate(published.getEligibilityRules(), facts);
        if (!eligible) {
            return new GuestEligibilityEvaluateResponse(false, null);
        }
        GuestEligibilityTicket ticket =
                new GuestEligibilityTicket(UUID.randomUUID(), organizationId, definition.getId(), facts);
        ticketStore.save(ticket, guestProperties.getTicketTtl());
        return new GuestEligibilityEvaluateResponse(true, ticket.id());
    }

    public GuestEligibilityTicket consume(UUID ticketId, UUID organizationId) {
        GuestEligibilityTicket ticket = ticketStore
                .consume(ticketId)
                .orElseThrow(() -> BusinessException.notFound("error.guest.ticket_not_found"));
        if (!ticket.organizationId().equals(organizationId)) {
            throw BusinessException.notFound("error.guest.ticket_not_found");
        }
        return ticket;
    }
}
