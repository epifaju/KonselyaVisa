package com.konselyavisa.privacy;

import static org.assertj.core.api.Assertions.assertThat;

import com.konselyavisa.applicant.ApplicantRepository;
import com.konselyavisa.catalog.CatalogIds;
import com.konselyavisa.dossier.CaseFileRepository;
import com.konselyavisa.dossier.CaseService;
import com.konselyavisa.dossier.api.ApplicantRequest;
import com.konselyavisa.dossier.api.CreateCaseRequest;
import com.konselyavisa.dossier.api.PrivacyConsentAcceptance;
import com.konselyavisa.organization.DemoOrganization;
import com.konselyavisa.outbox.domain.OutboxEventTypes;
import com.konselyavisa.outbox.persistence.OutboxEventRepository;
import com.konselyavisa.privacy.api.DataDeletionRequestResponse;
import com.konselyavisa.privacy.domain.DataDeletionRequest;
import com.konselyavisa.privacy.domain.DataDeletionStatus;
import com.konselyavisa.privacy.persistence.DataDeletionRequestRepository;
import com.konselyavisa.tenancy.TenantContext;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
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
class RetentionPurgeIT {

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
        registry.add("konselyavisa.privacy.purge-enabled", () -> "true");
        registry.add("konselyavisa.privacy.anonymize-after", () -> "1h");
    }

    @Autowired
    private CaseService caseService;

    @Autowired
    private PersonalDataRightsService personalDataRightsService;

    @Autowired
    private RetentionPurgeService retentionPurgeService;

    @Autowired
    private DataDeletionRequestRepository dataDeletionRequestRepository;

    @Autowired
    private ApplicantRepository applicantRepository;

    @Autowired
    private CaseFileRepository caseFileRepository;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        TenantContext.clear();
    }

    @Test
    void purgesDueDeletionRequestsAndAnonymizesApplicant() {
        String subject = "purge-citizen";
        authenticateCitizen(subject);
        TenantContext.setOrganizationId(DemoOrganization.ID);

        var created = caseService.create(new CreateCaseRequest(
                CatalogIds.VISA_TOURISM_FR_GW,
                new ApplicantRequest(
                        "purge@example.com", "Camara", Map.of("nationality", "FR", "passportValidityMonths", 12)),
                PrivacyConsentAcceptance.currentAccepted("fr")));

        DataDeletionRequestResponse deletion = personalDataRightsService.requestDeletion();
        assertThat(deletion.status()).isEqualTo(DataDeletionStatus.PENDING);

        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.executeWithoutResult(status -> {
            TenantContext.setPlatformAdmin(true);
            TenantContext.setOrganizationId(null);
            DataDeletionRequest request = dataDeletionRequestRepository.findById(deletion.id()).orElseThrow();
            request.setScheduledAnonymizeAt(Instant.now().minus(1, ChronoUnit.HOURS));
            dataDeletionRequestRepository.saveAndFlush(request);
        });

        int purged = retentionPurgeService.purgeDue();
        assertThat(purged).isEqualTo(1);

        template.executeWithoutResult(status -> {
            TenantContext.setOrganizationId(DemoOrganization.ID);
            DataDeletionRequest completed = dataDeletionRequestRepository.findById(deletion.id()).orElseThrow();
            assertThat(completed.getStatus()).isEqualTo(DataDeletionStatus.COMPLETED);
            assertThat(completed.getCompletedAt()).isNotNull();
            assertThat(applicantRepository.findByOrganizationIdAndKeycloakSubject(DemoOrganization.ID, subject))
                    .isEmpty();
            var caseFile = caseFileRepository.findById(created.id()).orElseThrow();
            assertThat(caseFile.getApplicant().getDisplayName()).isEqualTo("anonymized");
            assertThat(caseFile.getApplicant().getEmail()).isNull();
            assertThat(caseFile.getApplicantFacts()).isEmpty();
            assertThat(outboxEventRepository.findByAggregateIdAndEventTypeOrderByCreatedAtAsc(
                            deletion.id(), OutboxEventTypes.DATA_DELETION_COMPLETED))
                    .hasSize(1);
        });
    }

    private static void authenticateCitizen(String subject) {
        Jwt jwt = Jwt.withTokenValue("test")
                .header("alg", "none")
                .subject(subject)
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300))
                .claim("organization_id", DemoOrganization.ID.toString())
                .build();
        SecurityContextHolder.getContext()
                .setAuthentication(new JwtAuthenticationToken(
                        jwt, List.of(new SimpleGrantedAuthority("ROLE_CITIZEN"))));
    }
}
