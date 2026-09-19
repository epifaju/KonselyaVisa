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
class CatalogMvpProceduresIT {

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
    void flaggedProceduresHiddenUntilDemoOrgEnablesThem() {
        assertThat(procedureService.getById(CatalogIds.VISA_TOURISM_FR_GW, false).code())
                .isEqualTo("VISA_TOURISM_FR_GW");
        assertThatThrownBy(() -> procedureService.getById(CatalogIds.LEGALIZATION_FR_GW, false))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getMessageKey())
                .isEqualTo("error.catalog.procedure_not_found");
        assertThatThrownBy(() -> procedureService.getById(CatalogIds.APOSTILLE_FR_PT, false))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getMessageKey())
                .isEqualTo("error.catalog.procedure_not_found");
        assertThat(search(CatalogIds.FRANCE, CatalogIds.GUINEA_BISSAU, ProcedureCategory.LEGALIZATION, false)
                        .totalElements())
                .isZero();
        assertThat(search(CatalogIds.FRANCE, CatalogIds.PORTUGAL, ProcedureCategory.APOSTILLE, false)
                        .totalElements())
                .isZero();

        ProcedureResponse adminView = procedureService.getById(CatalogIds.LEGALIZATION_FR_GW, true);
        assertThat(adminView.code()).isEqualTo("LEGALIZATION_FR_GW");

        TenantContext.setOrganizationId(DemoOrganization.ID);
        ProcedureResponse legalization = procedureService.getById(CatalogIds.LEGALIZATION_FR_GW, false);
        ProcedureResponse apostille = procedureService.getById(CatalogIds.APOSTILLE_FR_PT, false);
        assertThat(legalization.publishedVersionNumber()).isEqualTo(1);
        assertThat(apostille.publishedVersionNumber()).isEqualTo(1);
        assertThat(((Number) legalization.versions().getFirst().pricing().get("amountMinor")).intValue())
                .isEqualTo(4500);
        assertThat(((Number) apostille.versions().getFirst().pricing().get("amountMinor")).intValue())
                .isEqualTo(3500);
        assertThat(search(CatalogIds.FRANCE, CatalogIds.GUINEA_BISSAU, ProcedureCategory.LEGALIZATION, false)
                        .totalElements())
                .isEqualTo(1);
        assertThat(search(CatalogIds.FRANCE, CatalogIds.PORTUGAL, ProcedureCategory.APOSTILLE, false)
                        .totalElements())
                .isEqualTo(1);
        assertThat(search(CatalogIds.FRANCE, CatalogIds.GUINEA_BISSAU, ProcedureCategory.VISA, false)
                        .totalElements())
                .isEqualTo(1);

        var legalizationRules = legalization.versions().getFirst().eligibilityRules();
        var apostilleRules = apostille.versions().getFirst().eligibilityRules();
        assertThat(eligibilityEvaluator.evaluate(
                        legalizationRules, Map.of("documentType", "DIPLOMA", "nationality", "GW")))
                .isTrue();
        assertThat(eligibilityEvaluator.evaluate(
                        legalizationRules, Map.of("documentType", "PHOTO", "nationality", "FR")))
                .isFalse();
        assertThat(eligibilityEvaluator.evaluate(
                        apostilleRules, Map.of("hagueConvention", true, "nationality", "FR")))
                .isTrue();
        assertThat(eligibilityEvaluator.evaluate(
                        apostilleRules, Map.of("hagueConvention", true, "nationality", "GW")))
                .isFalse();
    }

    @Test
    void demoOrgCanOpenLegalizationAndApostilleWhenEligible() {
        TenantContext.setOrganizationId(DemoOrganization.ID);

        var legalization = caseService.create(new CreateCaseRequest(
                CatalogIds.LEGALIZATION_FR_GW,
                new ApplicantRequest(
                        "legalization@example.com",
                        "Diallo",
                        Map.of("documentType", "BIRTH_CERTIFICATE", "nationality", "FR"))));
        assertThat(legalization.procedureDefinitionId()).isEqualTo(CatalogIds.LEGALIZATION_FR_GW);
        assertThat(legalization.eligibilityPassed()).isTrue();

        assertThatThrownBy(() -> caseService.create(new CreateCaseRequest(
                        CatalogIds.APOSTILLE_FR_PT,
                        new ApplicantRequest(
                                "apostille@example.com",
                                "Santos",
                                Map.of("hagueConvention", false, "nationality", "PT")))))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getMessageKey())
                .isEqualTo("error.eligibility.not_met");

        var apostille = caseService.create(new CreateCaseRequest(
                CatalogIds.APOSTILLE_FR_PT,
                new ApplicantRequest(
                        "apostille@example.com",
                        "Santos",
                        Map.of("hagueConvention", true, "nationality", "PT"))));
        assertThat(apostille.procedureDefinitionId()).isEqualTo(CatalogIds.APOSTILLE_FR_PT);
    }

    @Test
    void creatingFlaggedProcedureWithoutOrgFlagLooksMissing() {
        TenantContext.setOrganizationId(UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"));
        assertThatThrownBy(() -> caseService.create(new CreateCaseRequest(
                        CatalogIds.LEGALIZATION_FR_GW,
                        new ApplicantRequest(
                                "hidden@example.com",
                                "Camara",
                                Map.of("documentType", "BIRTH_CERTIFICATE", "nationality", "FR")))))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getMessageKey())
                .isEqualTo("error.catalog.procedure_not_found");
    }

    private PageResponse<ProcedureResponse> search(
            UUID origin, UUID destination, ProcedureCategory category, boolean includeDrafts) {
        return procedureService.search(origin, destination, category, includeDrafts, PageRequest.of(0, 10));
    }
}
