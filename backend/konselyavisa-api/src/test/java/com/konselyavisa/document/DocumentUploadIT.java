package com.konselyavisa.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.konselyavisa.catalog.CatalogIds;
import com.konselyavisa.common.exception.BusinessException;
import com.konselyavisa.document.api.DocumentResponse;
import com.konselyavisa.document.domain.DocumentAccessAction;
import com.konselyavisa.document.persistence.CaseDocumentRepository;
import com.konselyavisa.document.persistence.DocumentAccessLogRepository;
import com.konselyavisa.document.storage.ObjectStorage;
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
class DocumentUploadIT {

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
    private ObjectStorage objectStorage;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private CaseDocumentRepository caseDocumentRepository;

    @Autowired
    private DocumentAccessLogRepository documentAccessLogRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void uploadStoresObjectWritesOutboxAndDetectsDuplicateHash() {
        TenantContext.setOrganizationId(DemoOrganization.ID);
        CaseResponse firstCase = createEligibleCase("first@example.com", "Awa");
        byte[] payload = new byte[] {1, 2, 3, 4, 5, 42};
        MockMultipartFile file = new MockMultipartFile(
                "file", "passeport.jpg", "image/jpeg", payload);

        DocumentResponse uploaded = documentService.upload(firstCase.id(), "PASSPORT", file);

        assertThat(uploaded.requirementCode()).isEqualTo("PASSPORT");
        assertThat(uploaded.duplicateHash()).isFalse();
        assertThat(uploaded.sha256()).isEqualTo(DocumentFiles.sha256Hex(payload));
        assertThat(caseService.getById(firstCase.id()).status()).isEqualTo(CaseStatus.IN_PROGRESS);

        TransactionTemplate template = new TransactionTemplate(transactionManager);
        String storageKey = template.execute(
                status -> caseDocumentRepository.findById(uploaded.id()).orElseThrow().getStorageKey());
        assertThat(objectStorage.exists(storageKey)).isTrue();
        assertThat(objectStorage.get(storageKey)).isEqualTo(payload);

        List<OutboxEvent> events = template.execute(status ->
                outboxEventRepository.findByAggregateIdAndEventTypeOrderByCreatedAtAsc(
                        firstCase.id(), OutboxEventTypes.DOCUMENT_UPLOADED));
        assertThat(events).hasSize(1);
        assertThat(events.getFirst().getPayload())
                .containsEntry("documentId", uploaded.id().toString())
                .containsEntry("requirementCode", "PASSPORT")
                .containsEntry("sha256", uploaded.sha256())
                .doesNotContainKey("originalFilename");

        DocumentContent downloaded = documentService.download(firstCase.id(), uploaded.id());
        assertThat(downloaded.bytes()).isEqualTo(payload);
        Long accessLogs = template.execute(status -> documentAccessLogRepository.count());
        assertThat(accessLogs).isEqualTo(1);
        DocumentAccessAction action = template.execute(
                status -> documentAccessLogRepository.findAll().getFirst().getAction());
        assertThat(action).isEqualTo(DocumentAccessAction.DOWNLOAD);

        CaseResponse secondCase = createEligibleCase("second@example.com", "Binta");
        DocumentResponse duplicate = documentService.upload(
                secondCase.id(),
                "PASSPORT",
                new MockMultipartFile("file", "copy.jpg", "image/jpeg", payload));
        assertThat(duplicate.duplicateHash()).isTrue();
    }

    @Test
    void rejectsUndeclaredRequirementCode() {
        TenantContext.setOrganizationId(DemoOrganization.ID);
        CaseResponse created = createEligibleCase("photo@example.com", "Camara");
        assertThatThrownBy(() -> documentService.upload(
                        created.id(),
                        "BANK_STATEMENT",
                        new MockMultipartFile("file", "x.jpg", "image/jpeg", new byte[] {9})))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getMessageKey())
                .isEqualTo("error.document.requirement_unknown");
    }

    private CaseResponse createEligibleCase(String email, String name) {
        return caseService.create(new CreateCaseRequest(
                CatalogIds.VISA_TOURISM_FR_GW,
                new ApplicantRequest(email, name, Map.of("nationality", "PT", "passportValidityMonths", 12))));
    }
}
