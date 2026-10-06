package com.konselyavisa.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.konselyavisa.catalog.api.ProcedureResponse;
import com.konselyavisa.catalog.domain.ProcedureCategory;
import com.konselyavisa.catalog.service.ProcedureService;
import com.konselyavisa.common.api.PageResponse;
import com.konselyavisa.common.exception.BusinessException;
import com.konselyavisa.dossier.CaseService;
import com.konselyavisa.dossier.api.ApplicantRequest;
import com.konselyavisa.dossier.api.CreateCaseRequest;
import com.konselyavisa.eligibility.JsonLogicEligibilityEvaluator;
import com.konselyavisa.organization.DemoOrganization;
import com.konselyavisa.tenancy.TenantContext;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
@ActiveProfiles("test")
class CatalogExtendedProceduresIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("konselyavisa")
            .withUsername("konselyavisa")
            .withPassword("konselyavisa");

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", () -> "konselyavisa_app");
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.flyway.user", POSTGRES::getUsername);
        registry.add("spring.flyway.password", POSTGRES::getPassword);
        registry.add(
                "spring.security.oauth2.resourceserver.jwt.jwk-set-uri",
                () -> "http://127.0.0.1:1/realms/konselyavisa/protocol/openid-connect/certs");
        registry.add("management.otlp.tracing.export.enabled", () -> "false");
        registry.add("management.tracing.enabled", () -> "false");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }

    @Autowired
    private ProcedureService procedureService;

    @Autowired
    private CaseService caseService;

    @Autowired
    private JsonLogicEligibilityEvaluator eligibilityEvaluator;

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void evisaInsuranceTranslationHiddenUntilDemoOrgFlag() {
        assertThatThrownBy(() -> procedureService.getById(CatalogIds.EVISA_TOURISM_FR_GW, false))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getMessageKey())
                .isEqualTo("error.catalog.procedure_not_found");
        assertThatThrownBy(() -> procedureService.getById(CatalogIds.TRAVEL_INSURANCE_FR_GW, false))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getMessageKey())
                .isEqualTo("error.catalog.procedure_not_found");
        assertThatThrownBy(() -> procedureService.getById(CatalogIds.SWORN_TRANSLATION_FR_GW, false))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getMessageKey())
                .isEqualTo("error.catalog.procedure_not_found");

        TenantContext.setOrganizationId(DemoOrganization.ID);
        ProcedureResponse evisa = procedureService.getById(CatalogIds.EVISA_TOURISM_FR_GW, false);
        ProcedureResponse insurance = procedureService.getById(CatalogIds.TRAVEL_INSURANCE_FR_GW, false);
        ProcedureResponse translation = procedureService.getById(CatalogIds.SWORN_TRANSLATION_FR_GW, false);

        assertThat(evisa.category()).isEqualTo(ProcedureCategory.EVISA);
        assertThat(insurance.category()).isEqualTo(ProcedureCategory.INSURANCE);
        assertThat(translation.category()).isEqualTo(ProcedureCategory.TRANSLATION);
        assertThat(((Number) evisa.versions().getFirst().pricing().get("amountMinor")).intValue())
                .isEqualTo(6500);
        assertThat(((Number) insurance.versions().getFirst().pricing().get("amountMinor")).intValue())
                .isEqualTo(2500);
        assertThat(((Number) translation.versions().getFirst().pricing().get("amountMinor")).intValue())
                .isEqualTo(5500);
        assertThat(search(CatalogIds.FRANCE, CatalogIds.GUINEA_BISSAU, ProcedureCategory.EVISA, false)
                        .totalElements())
                .isEqualTo(1);
        assertThat(search(CatalogIds.FRANCE, CatalogIds.GUINEA_BISSAU, ProcedureCategory.INSURANCE, false)
                        .totalElements())
                .isEqualTo(1);
        assertThat(search(CatalogIds.FRANCE, CatalogIds.GUINEA_BISSAU, ProcedureCategory.TRANSLATION, false)
                        .totalElements())
                .isEqualTo(1);

        assertThat(eligibilityEvaluator.evaluate(
                        evisa.versions().getFirst().eligibilityRules(),
                        Map.of("passportValidityMonths", 12, "nationality", "FR")))
                .isTrue();
        assertThat(eligibilityEvaluator.evaluate(
                        insurance.versions().getFirst().eligibilityRules(),
                        Map.of("nationality", "PT", "tripDurationDays", 14)))
                .isTrue();
        assertThat(eligibilityEvaluator.evaluate(
                        insurance.versions().getFirst().eligibilityRules(),
                        Map.of("nationality", "PT", "tripDurationDays", 120)))
                .isFalse();
        assertThat(eligibilityEvaluator.evaluate(
                        translation.versions().getFirst().eligibilityRules(),
                        Map.of(
                                "documentType",
                                "DIPLOMA",
                                "nationality",
                                "FR",
                                "sourceLanguage",
                                "fr",
                                "targetLanguage",
                                "pt")))
                .isTrue();
    }

    @Test
    void demoOrgCanOpenEvisaInsuranceAndTranslationWhenEligible() {
        TenantContext.setOrganizationId(DemoOrganization.ID);

        var evisa = caseService.create(new CreateCaseRequest(
                CatalogIds.EVISA_TOURISM_FR_GW,
                new ApplicantRequest(
                        "evisa@example.com",
                        "Camara",
                        Map.of("passportValidityMonths", 12, "nationality", "GW"))));
        assertThat(evisa.procedureDefinitionId()).isEqualTo(CatalogIds.EVISA_TOURISM_FR_GW);
        assertThat(evisa.eligibilityPassed()).isTrue();

        var insurance = caseService.create(new CreateCaseRequest(
                CatalogIds.TRAVEL_INSURANCE_FR_GW,
                new ApplicantRequest(
                        "insurance@example.com",
                        "Diallo",
                        Map.of("nationality", "FR", "tripDurationDays", 21))));
        assertThat(insurance.procedureDefinitionId()).isEqualTo(CatalogIds.TRAVEL_INSURANCE_FR_GW);

        assertThatThrownBy(() -> caseService.create(new CreateCaseRequest(
                        CatalogIds.SWORN_TRANSLATION_FR_GW,
                        new ApplicantRequest(
                                "translation@example.com",
                                "Santos",
                                Map.of(
                                        "documentType",
                                        "DIPLOMA",
                                        "nationality",
                                        "FR",
                                        "sourceLanguage",
                                        "en",
                                        "targetLanguage",
                                        "pt")))))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getMessageKey())
                .isEqualTo("error.eligibility.not_met");

        var translation = caseService.create(new CreateCaseRequest(
                CatalogIds.SWORN_TRANSLATION_FR_GW,
                new ApplicantRequest(
                        "translation@example.com",
                        "Santos",
                        Map.of(
                                "documentType",
                                "DIPLOMA",
                                "nationality",
                                "FR",
                                "sourceLanguage",
                                "fr",
                                "targetLanguage",
                                "pt"))));
        assertThat(translation.procedureDefinitionId()).isEqualTo(CatalogIds.SWORN_TRANSLATION_FR_GW);
    }

    @Test
    void creatingWithoutOrgFlagLooksMissing() {
        TenantContext.setOrganizationId(UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"));
        assertThatThrownBy(() -> caseService.create(new CreateCaseRequest(
                        CatalogIds.EVISA_TOURISM_FR_GW,
                        new ApplicantRequest(
                                "hidden@example.com",
                                "Camara",
                                Map.of("passportValidityMonths", 12, "nationality", "FR")))))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getMessageKey())
                .isEqualTo("error.catalog.procedure_not_found");
    }

    private PageResponse<ProcedureResponse> search(
            UUID origin, UUID destination, ProcedureCategory category, boolean includeDrafts) {
        return procedureService.search(origin, destination, category, includeDrafts, PageRequest.of(0, 10));
    }
}
