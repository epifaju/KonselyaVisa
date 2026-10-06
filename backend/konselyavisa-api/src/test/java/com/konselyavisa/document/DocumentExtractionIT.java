package com.konselyavisa.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.konselyavisa.catalog.CatalogIds;
import com.konselyavisa.common.exception.BusinessException;
import com.konselyavisa.document.api.DocumentResponse;
import com.konselyavisa.document.api.SaveDocumentExtractionRequest;
import com.konselyavisa.document.crypto.ExtractedFieldsCrypto;
import com.konselyavisa.document.domain.CaseDocument;
import com.konselyavisa.document.domain.ExtractionSource;
import com.konselyavisa.document.persistence.CaseDocumentRepository;
import com.konselyavisa.dossier.CaseService;
import com.konselyavisa.dossier.api.ApplicantRequest;
import com.konselyavisa.dossier.api.CaseResponse;
import com.konselyavisa.dossier.api.CreateCaseRequest;
import com.konselyavisa.organization.DemoOrganization;
import com.konselyavisa.tenancy.TenantContext;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
@ActiveProfiles("test")
class DocumentExtractionIT {

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
    private DocumentExtractionService documentExtractionService;

    @Autowired
    private CaseDocumentRepository caseDocumentRepository;

    @Autowired
    private ExtractedFieldsCrypto extractedFieldsCrypto;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        TenantContext.clear();
    }

    @Test
    void agentSavesManualExtractionWithoutAiConfidence() {
        TenantContext.setOrganizationId(DemoOrganization.ID);
        CaseResponse created = caseService.create(new CreateCaseRequest(
                CatalogIds.VISA_TOURISM_FR_GW,
                new ApplicantRequest(
                        "extract@example.com",
                        "Awa",
                        Map.of("nationality", "PT", "passportValidityMonths", 12))));
        MultipartFile file = new MockMultipartFile("file", "passeport.jpg", "image/jpeg", new byte[] {1, 2, 3, 4});
        DocumentResponse uploaded = documentService.upload(created.id(), "PASSPORT", file);

        DocumentResponse saved = documentExtractionService.saveManual(
                created.id(),
                uploaded.id(),
                new SaveDocumentExtractionRequest(
                        Map.of("givenName", "Awa", "documentNumber", "P1234567"),
                        Map.of("mrz_valid", "UNCHECKED")));

        assertThat(saved.extractionSource()).isEqualTo(ExtractionSource.MANUAL);
        assertThat(saved.aiConfidence()).isNull();
        assertThat(saved.extractedFields()).containsEntry("givenname", "Awa").containsEntry("documentnumber", "P1234567");
        assertThat(saved.documentValidations()).containsEntry("mrz_valid", "UNCHECKED");
        assertThat(documentService.list(created.id()).getFirst().extractionSource()).isEqualTo(ExtractionSource.MANUAL);

        TransactionTemplate transactions = new TransactionTemplate(transactionManager);
        transactions.executeWithoutResult(status -> {
            CaseDocument stored = caseDocumentRepository.findById(uploaded.id()).orElseThrow();
            assertThat(stored.getExtractedFieldsCipher()).isNotEmpty();
            assertThat(new String(stored.getExtractedFieldsCipher(), StandardCharsets.ISO_8859_1))
                    .doesNotContain("P1234567");
            assertThat(extractedFieldsCrypto.decrypt(stored.getExtractedFieldsCipher()))
                    .containsEntry("documentnumber", "P1234567");
        });
    }

    @Test
    void citizenCannotSaveExtraction() {
        TenantContext.setOrganizationId(DemoOrganization.ID);
        CaseResponse created = caseService.create(new CreateCaseRequest(
                CatalogIds.VISA_TOURISM_FR_GW,
                new ApplicantRequest(
                        "citizen-extract@example.com",
                        "Awa",
                        Map.of("nationality", "PT", "passportValidityMonths", 12))));
        DocumentResponse uploaded = documentService.upload(
                created.id(),
                "PASSPORT",
                new MockMultipartFile("file", "passeport.jpg", "image/jpeg", new byte[] {5, 6}));
        authenticate("site-citizen", List.of("ROLE_CITIZEN"));
        assertThatThrownBy(() -> documentExtractionService.saveManual(
                        created.id(),
                        uploaded.id(),
                        new SaveDocumentExtractionRequest(Map.of("givenName", "Awa"), Map.of())))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getMessageKey())
                .isEqualTo("error.access.denied");
    }

    private static void authenticate(String subject, List<String> roles) {
        Jwt jwt = Jwt.withTokenValue("test")
                .header("alg", "none")
                .subject(subject)
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300))
                .claim("organization_id", DemoOrganization.ID.toString())
                .claim("preferred_username", subject)
                .build();
        SecurityContextHolder.getContext()
                .setAuthentication(new JwtAuthenticationToken(
                        jwt, roles.stream().map(SimpleGrantedAuthority::new).toList()));
    }
}
