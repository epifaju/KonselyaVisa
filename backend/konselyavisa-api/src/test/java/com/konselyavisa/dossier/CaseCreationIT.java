package com.konselyavisa.dossier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.konselyavisa.catalog.CatalogIds;
import com.konselyavisa.catalog.api.CreateProcedureRequest;
import com.konselyavisa.catalog.api.CreateProcedureVersionRequest;
import com.konselyavisa.catalog.api.ProcedureResponse;
import com.konselyavisa.catalog.api.ProcedureVersionResponse;
import com.konselyavisa.catalog.domain.ProcedureCategory;
import com.konselyavisa.catalog.service.ProcedureService;
import com.konselyavisa.common.exception.BusinessException;
import com.konselyavisa.dossier.api.ApplicantRequest;
import com.konselyavisa.dossier.api.CaseResponse;
import com.konselyavisa.dossier.api.CreateCaseRequest;
import com.konselyavisa.organization.DemoOrganization;
import com.konselyavisa.outbox.domain.OutboxEvent;
import com.konselyavisa.outbox.domain.OutboxEventTypes;
import com.konselyavisa.outbox.persistence.OutboxEventRepository;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
@ActiveProfiles("test")
class CaseCreationIT {

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
    private CaseService caseService;

    @Autowired
    private ProcedureService procedureService;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void createFreezesPublishedVersionAndWritesOutbox() {
        TenantContext.setOrganizationId(DemoOrganization.ID);
        ProcedureResponse procedure = procedureService.create(new CreateProcedureRequest(
                "VISA_FREEZE_FR_PT",
                CatalogIds.FRANCE,
                CatalogIds.PORTUGAL,
                ProcedureCategory.VISA,
                Map.of("fr", "Visa fige", "pt", "Visto congelado", "en", "Frozen visa"),
                Map.of("fr", "Test de figement de version"),
                Map.of(),
                List.of(Map.of(
                        "code",
                        "PASSPORT",
                        "required",
                        true,
                        "labelI18n",
                        Map.of("fr", "Passeport", "pt", "Passaporte", "en", "Passport")))));
        ProcedureVersionResponse v1 = procedureService.publish(procedure.id(), procedure.versions().getFirst().id());

        CaseResponse created = caseService.create(new CreateCaseRequest(
                procedure.id(),
                new ApplicantRequest("citizen@example.com", "Camara", Map.of("nationality", "PT"))));

        assertThat(created.procedureVersionId()).isEqualTo(v1.id());
        assertThat(created.procedureVersionNumber()).isEqualTo(1);
        assertThat(created.eligibilityPassed()).isTrue();
        assertThat(created.reference()).startsWith("KV-");

        TransactionTemplate template = new TransactionTemplate(transactionManager);
        List<OutboxEvent> events = template.execute(status ->
                outboxEventRepository.findByAggregateIdAndEventTypeOrderByCreatedAtAsc(
                        created.id(), OutboxEventTypes.CASE_CREATED));
        assertThat(events).hasSize(1);
        assertThat(events.getFirst().getPayload())
                .containsEntry("procedureVersionId", v1.id().toString());
        assertThat(((Number) events.getFirst().getPayload().get("procedureVersionNumber")).intValue())
                .isEqualTo(1);

        ProcedureVersionResponse draftV2 = procedureService.createVersion(
                procedure.id(), new CreateProcedureVersionRequest(null, Map.of("==", List.of(true, true)), List.of(), null));
        procedureService.publish(procedure.id(), draftV2.id());

        CaseResponse reloaded = caseService.getById(created.id());
        assertThat(reloaded.procedureVersionId()).isEqualTo(v1.id());
        assertThat(reloaded.procedureVersionNumber()).isEqualTo(1);
    }

    @Test
    void rejectsIneligibleApplicant() {
        TenantContext.setOrganizationId(DemoOrganization.ID);
        assertThatThrownBy(() -> caseService.create(new CreateCaseRequest(
                        CatalogIds.VISA_TOURISM_FR_GW,
                        new ApplicantRequest(
                                null,
                                null,
                                Map.of("nationality", "SN", "passportValidityMonths", 12)))))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getMessageKey())
                .isEqualTo("error.eligibility.not_met");
    }
}
