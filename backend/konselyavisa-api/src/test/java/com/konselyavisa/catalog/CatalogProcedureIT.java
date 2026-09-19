package com.konselyavisa.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.konselyavisa.catalog.api.CreateProcedureRequest;
import com.konselyavisa.catalog.api.CreateProcedureVersionRequest;
import com.konselyavisa.catalog.api.ProcedureResponse;
import com.konselyavisa.catalog.api.ProcedureVersionResponse;
import com.konselyavisa.catalog.api.UpdateProcedureVersionRequest;
import com.konselyavisa.catalog.domain.ProcedureCategory;
import com.konselyavisa.catalog.domain.ProcedureVersionStatus;
import com.konselyavisa.catalog.service.ProcedureService;
import com.konselyavisa.common.exception.BusinessException;
import java.util.List;
import java.util.Map;
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
class CatalogProcedureIT {

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

    @Test
    void seedVisaTourismIsPublishedForCitizens() {
        ProcedureResponse response = procedureService.getById(CatalogIds.VISA_TOURISM_FR_GW, false);
        assertThat(response.code()).isEqualTo("VISA_TOURISM_FR_GW");
        assertThat(response.publishedVersionNumber()).isEqualTo(1);
        assertThat(response.versions()).hasSize(1);
        assertThat(response.versions().getFirst().status()).isEqualTo(ProcedureVersionStatus.PUBLISHED);
        assertThat(procedureService
                        .search(
                                CatalogIds.FRANCE,
                                CatalogIds.GUINEA_BISSAU,
                                ProcedureCategory.VISA,
                                false,
                                PageRequest.of(0, 10))
                        .totalElements())
                .isEqualTo(1);
    }

    @Test
    void newVersionIsDraftUntilPublishedThenArchivesPrevious() {
        ProcedureResponse created = procedureService.create(new CreateProcedureRequest(
                "VISA_TEST_FR_PT",
                CatalogIds.FRANCE,
                CatalogIds.PORTUGAL,
                ProcedureCategory.VISA,
                Map.of("fr", "Visa test", "pt", "Visto teste", "en", "Test visa"),
                Map.of("fr", "Procédure de test"),
                Map.of("==", List.of(Map.of("var", "always"), true)),
                List.of(Map.of(
                        "code",
                        "PASSPORT",
                        "required",
                        true,
                        "labelI18n",
                        Map.of("fr", "Passeport", "pt", "Passaporte", "en", "Passport")))));

        ProcedureVersionResponse firstDraft = created.versions().getFirst();
        ProcedureVersionResponse published = procedureService.publish(created.id(), firstDraft.id());
        assertThat(published.status()).isEqualTo(ProcedureVersionStatus.PUBLISHED);

        ProcedureVersionResponse second = procedureService.createVersion(
                created.id(), new CreateProcedureVersionRequest(published.id(), null, null, null));
        assertThat(second.versionNumber()).isEqualTo(2);
        assertThat(second.status()).isEqualTo(ProcedureVersionStatus.DRAFT);
        assertThat(second.eligibilityRules()).containsKey("==");

        procedureService.updateVersion(
                created.id(),
                second.id(),
                new UpdateProcedureVersionRequest(Map.of("==", List.of(Map.of("var", "always"), true)), null, null));
        ProcedureVersionResponse publishedV2 = procedureService.publish(created.id(), second.id());
        assertThat(publishedV2.status()).isEqualTo(ProcedureVersionStatus.PUBLISHED);

        ProcedureResponse afterPublish = procedureService.getById(created.id(), true);
        assertThat(afterPublish.publishedVersionNumber()).isEqualTo(2);
        assertThat(afterPublish.versions())
                .anyMatch(version ->
                        version.versionNumber() == 1 && version.status() == ProcedureVersionStatus.ARCHIVED);
        assertThatThrownBy(() -> procedureService.updateVersion(
                        created.id(), published.id(), new UpdateProcedureVersionRequest(Map.of(), List.of(), null)))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getMessageKey())
                .isEqualTo("error.catalog.version_immutable");
    }
}
