package com.konselyavisa.dossier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.konselyavisa.catalog.CatalogIds;
import com.konselyavisa.common.exception.BusinessException;
import com.konselyavisa.dossier.api.ApplicantRequest;
import com.konselyavisa.dossier.api.CreateCaseRequest;
import com.konselyavisa.dossier.api.SupervisorSummaryResponse;
import com.konselyavisa.organization.DemoOrganization;
import com.konselyavisa.tenancy.TenantContext;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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
class SupervisorSummaryIT {

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
    private SupervisorSummaryService supervisorSummaryService;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        TenantContext.clear();
    }

    @Test
    void countsOpenWatchAndOverdueFromFrozenSla() {
        TenantContext.setOrganizationId(DemoOrganization.ID);
        authenticate("supervisor.dev", List.of("ROLE_SUPERVISOR"));

        UUID overdueId = caseService
                .create(new CreateCaseRequest(
                        CatalogIds.VISA_TOURISM_FR_GW,
                        new ApplicantRequest(
                                "overdue@example.com",
                                "Overdue",
                                Map.of("nationality", "PT", "passportValidityMonths", 12))))
                .id();
        UUID watchId = caseService
                .create(new CreateCaseRequest(
                        CatalogIds.VISA_TOURISM_FR_GW,
                        new ApplicantRequest(
                                "watch@example.com",
                                "Watch",
                                Map.of("nationality", "PT", "passportValidityMonths", 12))))
                .id();
        caseService.create(new CreateCaseRequest(
                CatalogIds.VISA_TOURISM_FR_GW,
                new ApplicantRequest(
                        "fresh@example.com",
                        "Fresh",
                        Map.of("nationality", "PT", "passportValidityMonths", 12))));

        backdate(overdueId, Instant.now().minus(6, ChronoUnit.DAYS));
        backdate(watchId, Instant.now().minus(3, ChronoUnit.DAYS));

        SupervisorSummaryResponse summary = supervisorSummaryService.summarize();
        assertThat(summary.openCount()).isEqualTo(3);
        assertThat(summary.overdueCount()).isEqualTo(1);
        assertThat(summary.watchCount()).isEqualTo(1);

        authenticate("agent.dev", List.of("ROLE_AGENT"));
        assertThatThrownBy(() -> supervisorSummaryService.summarize())
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getMessageKey())
                .isEqualTo("error.access.denied");
    }

    private void backdate(UUID caseId, Instant createdAt) {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.executeWithoutResult(status -> entityManager
                .createNativeQuery("update cases set created_at = :createdAt where id = :id")
                .setParameter("createdAt", createdAt)
                .setParameter("id", caseId)
                .executeUpdate());
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
