package com.konselyavisa.guest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.konselyavisa.applicant.Applicant;
import com.konselyavisa.catalog.domain.ProcedureDefinition;
import com.konselyavisa.common.exception.BusinessException;
import com.konselyavisa.dossier.CaseFile;
import com.konselyavisa.dossier.CaseFileRepository;
import com.konselyavisa.dossier.CaseStatus;
import com.konselyavisa.tenancy.TenantContext;
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
class PublicCaseLookupServiceTest {

    @Mock
    private GuestEligibilityService guestEligibilityService;

    @Mock
    private CaseFileRepository caseFileRepository;

    private PublicCaseLookupService service;
    private UUID orgId;

    @BeforeEach
    void setUp() {
        orgId = UUID.randomUUID();
        service = new PublicCaseLookupService(guestEligibilityService, caseFileRepository);
        TenantContext.clear();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void lookupRequiresIdentity() {
        assertThatThrownBy(() -> service.lookup(new PublicCaseLookupRequest(orgId, "KV-1", null, null)))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getMessageKey())
                .isEqualTo("error.guest.lookup_identity_required");
    }

    @Test
    void lookupRejectsUnknownReference() {
        when(guestEligibilityService.bindOrganization(orgId)).thenReturn(orgId);
        when(caseFileRepository.findByOrganizationIdAndReferenceIgnoreCase(orgId, "KV-MISSING"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                        service.lookup(new PublicCaseLookupRequest(orgId, "KV-MISSING", "a@b.c", null)))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getMessageKey())
                .isEqualTo("error.guest.case_not_found");
    }

    @Test
    void lookupMatchesEmailIgnoringCaseAndWhitespace() {
        when(guestEligibilityService.bindOrganization(orgId)).thenReturn(orgId);
        CaseFile caseFile = openCase("Citizen@Example.COM ", null);
        when(caseFileRepository.findByOrganizationIdAndReferenceIgnoreCase(orgId, "KV-1"))
                .thenReturn(Optional.of(caseFile));

        PublicCaseStatusResponse response =
                service.lookup(new PublicCaseLookupRequest(orgId, " KV-1 ", "  citizen@example.com ", null));

        assertThat(response.reference()).isEqualTo("KV-1");
        assertThat(response.status()).isEqualTo(CaseStatus.IN_PROGRESS);
        assertThat(response.nextActionMessageKey()).isEqualTo("case.status.IN_PROGRESS");
    }

    @Test
    void lookupMatchesDateOfBirthViaBirthDateFact() {
        when(guestEligibilityService.bindOrganization(orgId)).thenReturn(orgId);
        CaseFile caseFile = openCase(null, Map.of("birthDate", "1990-05-01"));
        when(caseFileRepository.findByOrganizationIdAndReferenceIgnoreCase(orgId, "KV-DOB"))
                .thenReturn(Optional.of(caseFile));

        PublicCaseStatusResponse response =
                service.lookup(new PublicCaseLookupRequest(orgId, "KV-DOB", null, "1990-05-01"));

        assertThat(response.reference()).isEqualTo("KV-DOB");
        assertThat(response.procedureNameI18n()).containsEntry("fr", "Visa");
    }

    @Test
    void lookupRejectsIdentityMismatchAsNotFound() {
        when(guestEligibilityService.bindOrganization(orgId)).thenReturn(orgId);
        CaseFile caseFile = openCase("owner@example.com", null);
        when(caseFileRepository.findByOrganizationIdAndReferenceIgnoreCase(orgId, "KV-1"))
                .thenReturn(Optional.of(caseFile));

        assertThatThrownBy(() ->
                        service.lookup(new PublicCaseLookupRequest(orgId, "KV-1", "other@example.com", null)))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getMessageKey())
                .isEqualTo("error.guest.case_not_found");
    }

    private CaseFile openCase(String email, Map<String, Object> facts) {
        Applicant applicant = new Applicant();
        applicant.setEmail(email);
        ProcedureDefinition definition = new ProcedureDefinition();
        definition.setNameI18n(Map.of("fr", "Visa"));
        CaseFile caseFile = new CaseFile();
        caseFile.setReference(facts == null ? "KV-1" : "KV-DOB");
        caseFile.setStatus(CaseStatus.IN_PROGRESS);
        caseFile.setApplicant(applicant);
        caseFile.setApplicantFacts(facts);
        caseFile.setProcedureDefinition(definition);
        return caseFile;
    }
}
