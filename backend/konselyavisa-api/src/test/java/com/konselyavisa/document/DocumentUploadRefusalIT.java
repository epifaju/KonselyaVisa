package com.konselyavisa.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.konselyavisa.catalog.CatalogIds;
import com.konselyavisa.common.exception.BusinessException;
import com.konselyavisa.document.api.DocumentUploadRefusalResponse;
import com.konselyavisa.document.persistence.DocumentUploadRefusalRepository;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
@ActiveProfiles("test")
class DocumentUploadRefusalIT {

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
    private DocumentUploadRefusalService documentUploadRefusalService;

    @Autowired
    private DocumentUploadRefusalRepository documentUploadRefusalRepository;

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void refusedUploadsAreJournaledAndListedForAgents() {
        TenantContext.setOrganizationId(DemoOrganization.ID);
        CaseResponse created = createEligibleCase("refusal@example.com", "Diallo");

        assertThatThrownBy(() -> documentService.upload(
                        created.id(),
                        "BANK_STATEMENT",
                        new MockMultipartFile("file", "x.jpg", "image/jpeg", new byte[] {9})))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getMessageKey())
                .isEqualTo("error.document.requirement_unknown");

        assertThatThrownBy(() -> documentService.upload(
                        created.id(),
                        "PASSPORT",
                        new MockMultipartFile("file", "secret.exe", "application/octet-stream", new byte[] {1, 2, 3})))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getMessageKey())
                .isEqualTo("error.document.content_type_not_allowed");

        assertThat(documentUploadRefusalRepository.count()).isEqualTo(2);

        List<DocumentUploadRefusalResponse> refusals = documentUploadRefusalService.listForCase(created.id());
        assertThat(refusals).hasSize(2);
        assertThat(refusals)
                .extracting(DocumentUploadRefusalResponse::reasonKey)
                .containsExactly(
                        "error.document.content_type_not_allowed", "error.document.requirement_unknown");
        assertThat(refusals.getFirst().requirementCode()).isEqualTo("PASSPORT");
        assertThat(refusals.getFirst().originalFilename()).isEqualTo("secret.exe");
        assertThat(refusals.getFirst().contentType()).isEqualTo("application/octet-stream");
        assertThat(refusals.getFirst().sizeBytes()).isEqualTo(3L);
        assertThat(refusals)
                .allSatisfy(refusal -> assertThat(refusal)
                        .extracting(DocumentUploadRefusalResponse::id)
                        .isNotNull());
        // SHA stays server-side only — never exposed on the agent list DTO.
        assertThat(refusals.getFirst().getClass().getRecordComponents())
                .extracting(component -> component.getName())
                .doesNotContain("sha256");
    }

    @Test
    void successfulUploadDoesNotCreateRefusal() {
        TenantContext.setOrganizationId(DemoOrganization.ID);
        CaseResponse created = createEligibleCase("ok@example.com", "Sow");
        long before = documentUploadRefusalRepository.count();

        documentService.upload(
                created.id(),
                "PASSPORT",
                new MockMultipartFile("file", "passeport.jpg", "image/jpeg", new byte[] {4, 5, 6}));

        assertThat(documentUploadRefusalRepository.count()).isEqualTo(before);
        assertThat(documentUploadRefusalService.listForCase(created.id())).isEmpty();
    }

    private CaseResponse createEligibleCase(String email, String name) {
        return caseService.create(new CreateCaseRequest(
                CatalogIds.VISA_TOURISM_FR_GW,
                new ApplicantRequest(email, name, Map.of("nationality", "PT", "passportValidityMonths", 12))));
    }
}
