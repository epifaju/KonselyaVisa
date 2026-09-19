package com.konselyavisa.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.konselyavisa.catalog.CatalogIds;
import com.konselyavisa.common.exception.BusinessException;
import com.konselyavisa.document.api.DocumentResponse;
import com.konselyavisa.document.api.DocumentReviewRequest;
import com.konselyavisa.document.domain.DocumentStatus;
import com.konselyavisa.dossier.CaseNextAction;
import com.konselyavisa.dossier.CaseService;
import com.konselyavisa.dossier.CaseStatus;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
@ActiveProfiles("test")
class DocumentReviewIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("konselyavisa")
            .withUsername("konselyavisa")
            .withPassword("konselyavisa");

    @Container
    @SuppressWarnings("resource")
    static final GenericContainer<?> MINIO = new GenericContainer<>("minio/minio:latest")
            .withEnv("MINIO_ROOT_USER", "minio")
            .withEnv("MINIO_ROOT_PASSWORD", "minioDevPass1")
            .withCommand("server", "/data")
            .withExposedPorts(9000)
            .waitingFor(Wait.forListeningPort());

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
        registry.add(
                "konselyavisa.storage.endpoint",
                () -> "http://" + MINIO.getHost() + ":" + MINIO.getMappedPort(9000));
        registry.add("konselyavisa.storage.access-key", () -> "minio");
        registry.add("konselyavisa.storage.secret-key", () -> "minioDevPass1");
        registry.add("konselyavisa.storage.bucket", () -> "konselyavisa-docs");
        registry.add("konselyavisa.storage.auto-create-bucket", () -> "true");
    }

    @Autowired
    private CaseService caseService;

    @Autowired
    private DocumentService documentService;

    @Autowired
    private DocumentReviewService documentReviewService;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void agentCanAcceptAndRequestCorrection() {
        TenantContext.setOrganizationId(DemoOrganization.ID);
        CaseResponse created = createEligibleCase();
        DocumentResponse uploaded = documentService.upload(
                created.id(),
                "PASSPORT",
                new MockMultipartFile("file", "passeport.jpg", "image/jpeg", new byte[] {1, 2, 3, 4}));

        DocumentResponse accepted = documentReviewService.accept(created.id(), uploaded.id());
        assertThat(accepted.status()).isEqualTo(DocumentStatus.ACCEPTED);

        DocumentResponse correction = documentReviewService.requestCorrection(
                created.id(), uploaded.id(), new DocumentReviewRequest("page 2 illisible"));
        assertThat(correction.status()).isEqualTo(DocumentStatus.CORRECTION_REQUESTED);
        CaseResponse afterCorrection = caseService.getById(created.id());
        assertThat(afterCorrection.status()).isEqualTo(CaseStatus.CORRECTION_REQUESTED);
        assertThat(afterCorrection.nextAction()).isEqualTo(CaseNextAction.CORRECT_DOCUMENTS);
        assertThat(afterCorrection.correctionMessageKey()).isEqualTo("error.document.correction_requested");

        TransactionTemplate template = new TransactionTemplate(transactionManager);
        List<OutboxEvent> events = template.execute(status ->
                outboxEventRepository.findByAggregateIdAndEventTypeOrderByCreatedAtAsc(
                        created.id(), OutboxEventTypes.CORRECTION_REQUESTED));
        assertThat(events).hasSize(1);
        assertThat(events.getFirst().getPayload())
                .containsEntry("documentId", uploaded.id().toString())
                .containsEntry("reason", "page 2 illisible")
                .containsEntry("messageKey", "error.document.correction_requested")
                .containsEntry("decision", "CORRECTION_REQUESTED");
    }

    @Test
    void acceptClearsCorrectionWhenNoPendingDocument() {
        TenantContext.setOrganizationId(DemoOrganization.ID);
        CaseResponse created = createEligibleCase();
        DocumentResponse uploaded = documentService.upload(
                created.id(),
                "PASSPORT",
                new MockMultipartFile("file", "passeport.jpg", "image/jpeg", new byte[] {9, 8, 7}));
        documentReviewService.requestCorrection(
                created.id(), uploaded.id(), new DocumentReviewRequest("photo trop sombre"));
        documentReviewService.accept(created.id(), uploaded.id());
        assertThat(caseService.getById(created.id()).status()).isEqualTo(CaseStatus.IN_PROGRESS);
    }

    @Test
    void rejectedDocumentCannotBeAccepted() {
        TenantContext.setOrganizationId(DemoOrganization.ID);
        CaseResponse created = createEligibleCase();
        DocumentResponse uploaded = documentService.upload(
                created.id(),
                "PASSPORT",
                new MockMultipartFile("file", "passeport.jpg", "image/jpeg", new byte[] {4}));
        DocumentResponse rejected = documentReviewService.reject(
                created.id(), uploaded.id(), new DocumentReviewRequest("faux document"));
        assertThat(rejected.status()).isEqualTo(DocumentStatus.REJECTED);
        assertThatThrownBy(() -> documentReviewService.accept(created.id(), uploaded.id()))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getMessageKey())
                .isEqualTo("error.document.not_reviewable");
    }

    private CaseResponse createEligibleCase() {
        return caseService.create(new CreateCaseRequest(
                CatalogIds.VISA_TOURISM_FR_GW,
                new ApplicantRequest(
                        "agent-review@example.com",
                        "Awa",
                        Map.of("nationality", "PT", "passportValidityMonths", 12))));
    }
}
