package com.konselyavisa.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.konselyavisa.catalog.api.CreateProcedureRequest;
import com.konselyavisa.catalog.api.CreateProcedureVersionRequest;
import com.konselyavisa.catalog.api.ProcedureResponse;
import com.konselyavisa.catalog.api.ProcedureVersionResponse;
import com.konselyavisa.catalog.api.UpdateProcedureRequest;
import com.konselyavisa.catalog.api.UpdateProcedureVersionRequest;
import com.konselyavisa.catalog.domain.ProcedureCategory;
import com.konselyavisa.catalog.domain.ProcedureVersionStatus;
import com.konselyavisa.catalog.service.ProcedureService;
import com.konselyavisa.common.exception.BusinessException;
import com.konselyavisa.dossier.CaseService;
import com.konselyavisa.dossier.api.ApplicantRequest;
import com.konselyavisa.dossier.api.CaseResponse;
import com.konselyavisa.dossier.api.CreateCaseRequest;
import com.konselyavisa.organization.DemoOrganization;
import com.konselyavisa.tenancy.TenantContext;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
@ActiveProfiles("test")
class CatalogPublishFrozenCaseIT {

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

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void businessAdminPublishDoesNotMoveOpenCasesToNewVersion() {
        TenantContext.setOrganizationId(DemoOrganization.ID);
        ProcedureResponse created = procedureService.create(new CreateProcedureRequest(
                "VISA_CATALOG_ADMIN_FR_PT",
                CatalogIds.FRANCE,
                CatalogIds.PORTUGAL,
                ProcedureCategory.VISA,
                Map.of("fr", "Visa catalogue", "pt", "Visto catálogo", "en", "Catalog visa"),
                Map.of("fr", "v1", "pt", "v1", "en", "v1"),
                Map.of(),
                List.of(requirement("PASSPORT", "Passeport", "Passaporte", "Passport"))));
        ProcedureVersionResponse firstDraft = created.versions().getFirst();
        procedureService.updateVersion(
                created.id(), firstDraft.id(), new UpdateProcedureVersionRequest(null, null, 4));
        ProcedureVersionResponse v1 = procedureService.publish(created.id(), firstDraft.id());

        CaseResponse openCase = caseService.create(new CreateCaseRequest(
                created.id(),
                new ApplicantRequest("catalog.admin@example.com", "Admin Case", Map.of("nationality", "PT"))));
        assertThat(openCase.procedureVersionId()).isEqualTo(v1.id());
        assertThat(openCase.procedureVersionNumber()).isEqualTo(1);
        assertThat(openCase.estimatedInstructionDays()).isEqualTo(4);

        procedureService.update(
                created.id(),
                new UpdateProcedureRequest(
                        null,
                        null,
                        null,
                        Map.of("fr", "Visa catalogue v2", "pt", "Visto catálogo v2", "en", "Catalog visa v2"),
                        Map.of("fr", "libellé mis à jour"),
                        null));

        ProcedureVersionResponse draft = procedureService.createVersion(
                created.id(), new CreateProcedureVersionRequest(v1.id(), null, null, null));
        procedureService.updateVersion(
                created.id(),
                draft.id(),
                new UpdateProcedureVersionRequest(
                        Map.of(),
                        List.of(
                                requirement("PASSPORT", "Passeport", "Passaporte", "Passport"),
                                requirement("PHOTO", "Photo", "Foto", "Photo")),
                        12));
        ProcedureVersionResponse v2 = procedureService.publish(created.id(), draft.id());
        assertThat(v2.status()).isEqualTo(ProcedureVersionStatus.PUBLISHED);
        assertThat(v2.documentRequirements()).hasSize(2);

        CaseResponse stillFrozen = caseService.getById(openCase.id());
        assertThat(stillFrozen.procedureVersionId()).isEqualTo(v1.id());
        assertThat(stillFrozen.procedureVersionNumber()).isEqualTo(1);
        assertThat(stillFrozen.estimatedInstructionDays()).isEqualTo(4);

        ProcedureResponse catalogView = procedureService.getById(created.id(), true);
        assertThat(catalogView.publishedVersionNumber()).isEqualTo(2);
        assertThat(catalogView.versions())
                .anyMatch(version ->
                        version.versionNumber() == 2
                                && version.status() == ProcedureVersionStatus.PUBLISHED
                                && version.documentRequirements().size() == 2
                                && Integer.valueOf(12).equals(version.estimatedInstructionDays()));

        assertThatThrownBy(() -> procedureService.updateVersion(
                        created.id(), v1.id(), new UpdateProcedureVersionRequest(Map.of(), List.of(), null)))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getMessageKey())
                .isEqualTo("error.catalog.version_immutable");
    }

    private static Map<String, Object> requirement(String code, String fr, String pt, String en) {
        return Map.of(
                "code",
                code,
                "required",
                true,
                "labelI18n",
                Map.of("fr", fr, "pt", pt, "en", en));
    }
}
