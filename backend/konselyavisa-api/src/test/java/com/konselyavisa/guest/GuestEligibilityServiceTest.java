package com.konselyavisa.guest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.konselyavisa.catalog.domain.ProcedureDefinition;
import com.konselyavisa.catalog.domain.ProcedureVersion;
import com.konselyavisa.catalog.domain.ProcedureVersionStatus;
import com.konselyavisa.catalog.persistence.ProcedureDefinitionRepository;
import com.konselyavisa.catalog.persistence.ProcedureVersionRepository;
import com.konselyavisa.catalog.service.ProcedureAccessGuard;
import com.konselyavisa.common.exception.BusinessException;
import com.konselyavisa.eligibility.JsonLogicEligibilityEvaluator;
import com.konselyavisa.organization.OrganizationDomainLookup;
import com.konselyavisa.organization.persistence.OrganizationRepository;
import com.konselyavisa.tenancy.TenantContext;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GuestEligibilityServiceTest {

    @Mock
    private GuestEligibilityTicketStore ticketStore;

    @Mock
    private OrganizationRepository organizationRepository;

    @Mock
    private OrganizationDomainLookup organizationDomainLookup;

    @Mock
    private ProcedureDefinitionRepository procedureDefinitionRepository;

    @Mock
    private ProcedureVersionRepository procedureVersionRepository;

    @Mock
    private ProcedureAccessGuard procedureAccessGuard;

    @Mock
    private JsonLogicEligibilityEvaluator eligibilityEvaluator;

    private GuestProperties guestProperties;
    private GuestEligibilityService service;
    private UUID orgId;
    private UUID procedureId;

    @BeforeEach
    void setUp() {
        orgId = UUID.randomUUID();
        procedureId = UUID.randomUUID();
        guestProperties = new GuestProperties();
        guestProperties.setTicketTtl(Duration.ofMinutes(15));
        guestProperties.setDefaultOrganizationId(orgId);
        service = new GuestEligibilityService(
                guestProperties,
                ticketStore,
                organizationRepository,
                organizationDomainLookup,
                procedureDefinitionRepository,
                procedureVersionRepository,
                procedureAccessGuard,
                eligibilityEvaluator);
        TenantContext.clear();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void bindOrganizationResolvesDomainHintAndSetsTenant() {
        UUID resolved = UUID.randomUUID();
        when(organizationDomainLookup.findOrganizationIdByDomain("visa.example"))
                .thenReturn(Optional.of(resolved));
        when(organizationRepository.existsById(resolved)).thenReturn(true);

        assertThat(service.bindOrganization(null, "visa.example")).isEqualTo(resolved);
        assertThat(TenantContext.getOrganizationId()).isEqualTo(resolved);
    }

    @Test
    void bindOrganizationRejectsUnknownOrg() {
        when(organizationRepository.existsById(orgId)).thenReturn(false);

        assertThatThrownBy(() -> service.bindOrganization(orgId))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getMessageKey())
                .isEqualTo("error.organization.not_found");
    }

    @Test
    void evaluatePersistsTicketWhenEligible() {
        when(organizationRepository.existsById(orgId)).thenReturn(true);
        ProcedureDefinition definition = activeProcedure();
        ProcedureVersion version = publishedVersion(definition);
        when(procedureDefinitionRepository.findById(procedureId)).thenReturn(Optional.of(definition));
        when(procedureVersionRepository.findByProcedureDefinitionIdAndStatus(
                        procedureId, ProcedureVersionStatus.PUBLISHED))
                .thenReturn(Optional.of(version));
        when(eligibilityEvaluator.evaluate(eq(version.getEligibilityRules()), any())).thenReturn(true);

        GuestEligibilityEvaluateResponse response = service.evaluate(
                new GuestEligibilityEvaluateRequest(orgId, procedureId, Map.of("age", 30)));

        assertThat(response.eligible()).isTrue();
        assertThat(response.ticketId()).isNotNull();
        ArgumentCaptor<GuestEligibilityTicket> ticketCaptor = ArgumentCaptor.forClass(GuestEligibilityTicket.class);
        verify(ticketStore).save(ticketCaptor.capture(), eq(Duration.ofMinutes(15)));
        assertThat(ticketCaptor.getValue().organizationId()).isEqualTo(orgId);
        assertThat(ticketCaptor.getValue().procedureDefinitionId()).isEqualTo(procedureId);
    }

    @Test
    void evaluateReturnsWithoutTicketWhenNotEligible() {
        when(organizationRepository.existsById(orgId)).thenReturn(true);
        ProcedureDefinition definition = activeProcedure();
        ProcedureVersion version = publishedVersion(definition);
        when(procedureDefinitionRepository.findById(procedureId)).thenReturn(Optional.of(definition));
        when(procedureVersionRepository.findByProcedureDefinitionIdAndStatus(
                        procedureId, ProcedureVersionStatus.PUBLISHED))
                .thenReturn(Optional.of(version));
        when(eligibilityEvaluator.evaluate(any(), any())).thenReturn(false);

        GuestEligibilityEvaluateResponse response = service.evaluate(
                new GuestEligibilityEvaluateRequest(orgId, procedureId, Map.of("age", 16)));

        assertThat(response.eligible()).isFalse();
        assertThat(response.ticketId()).isNull();
        verify(ticketStore, never()).save(any(), any());
    }

    @Test
    void evaluateRejectsMissingPublishedVersion() {
        when(organizationRepository.existsById(orgId)).thenReturn(true);
        when(procedureDefinitionRepository.findById(procedureId)).thenReturn(Optional.of(activeProcedure()));
        when(procedureVersionRepository.findByProcedureDefinitionIdAndStatus(
                        procedureId, ProcedureVersionStatus.PUBLISHED))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.evaluate(
                        new GuestEligibilityEvaluateRequest(orgId, procedureId, Map.of("age", 30))))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getMessageKey())
                .isEqualTo("error.case.procedure_not_published");
    }

    @Test
    void consumeRejectsOrgMismatch() {
        UUID ticketId = UUID.randomUUID();
        GuestEligibilityTicket ticket =
                new GuestEligibilityTicket(ticketId, UUID.randomUUID(), procedureId, Map.of());
        when(ticketStore.consume(ticketId)).thenReturn(Optional.of(ticket));

        assertThatThrownBy(() -> service.consume(ticketId, orgId))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getMessageKey())
                .isEqualTo("error.guest.ticket_not_found");
    }

    private ProcedureDefinition activeProcedure() {
        ProcedureDefinition definition = new ProcedureDefinition();
        definition.setId(procedureId);
        definition.setActive(true);
        definition.setCode("VISA_TOURISM");
        return definition;
    }

    private ProcedureVersion publishedVersion(ProcedureDefinition definition) {
        ProcedureVersion version = new ProcedureVersion();
        version.setId(UUID.randomUUID());
        version.setProcedureDefinition(definition);
        version.setStatus(ProcedureVersionStatus.PUBLISHED);
        version.setEligibilityRules(Map.of("==", 1));
        return version;
    }
}
