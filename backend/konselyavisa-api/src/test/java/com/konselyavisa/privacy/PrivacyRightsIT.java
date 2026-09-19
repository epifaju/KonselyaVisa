package com.konselyavisa.privacy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.konselyavisa.catalog.CatalogIds;
import com.konselyavisa.common.exception.BusinessException;
import com.konselyavisa.dossier.CaseService;
import com.konselyavisa.dossier.api.ApplicantRequest;
import com.konselyavisa.dossier.api.CreateCaseRequest;
import com.konselyavisa.dossier.api.PrivacyConsentAcceptance;
import com.konselyavisa.organization.DemoOrganization;
import com.konselyavisa.privacy.api.DataDeletionRequestResponse;
import com.konselyavisa.privacy.api.PersonalDataExportResponse;
import com.konselyavisa.privacy.domain.DataDeletionStatus;
import com.konselyavisa.tenancy.TenantContext;
import java.time.Instant;
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
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
@ActiveProfiles("test")
class PrivacyRightsIT {

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
        registry.add("konselyavisa.privacy.anonymize-after", () -> "10d");
    }

    @Autowired
    private CaseService caseService;

    @Autowired
    private PersonalDataRightsService personalDataRightsService;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        TenantContext.clear();
    }

    @Test
    void recordsConsentExportsDataAndDefersDeletion() {
        String subject = "privacy-citizen";
        authenticateCitizen(subject);
        TenantContext.setOrganizationId(DemoOrganization.ID);

        var created = caseService.create(new CreateCaseRequest(
                CatalogIds.VISA_TOURISM_FR_GW,
                new ApplicantRequest(
                        "privacy@example.com", "Diallo", Map.of("nationality", "PT", "passportValidityMonths", 12)),
                PrivacyConsentAcceptance.currentAccepted("fr")));

        PersonalDataExportResponse export = personalDataRightsService.exportMine();
        assertThat(export.keycloakSubject()).isEqualTo(subject);
        assertThat(export.applicant().email()).isEqualTo("privacy@example.com");
        assertThat(export.cases()).extracting(PersonalDataExportResponse.CaseExport::reference)
                .contains(created.reference());
        assertThat(export.consents()).isNotEmpty();
        assertThat(export.consents().getFirst().purpose()).isEqualTo("CASE_DEPOSIT");
        assertThat(export.consents().getFirst().noticeVersion()).isEqualTo(PrivacyNoticeCatalog.CURRENT_VERSION);

        DataDeletionRequestResponse first = personalDataRightsService.requestDeletion();
        assertThat(first.status()).isEqualTo(DataDeletionStatus.PENDING);
        assertThat(first.scheduledAnonymizeAt()).isAfter(Instant.now().plusSeconds(60));
        DataDeletionRequestResponse second = personalDataRightsService.requestDeletion();
        assertThat(second.id()).isEqualTo(first.id());
    }

    @Test
    void rejectsStaleNoticeVersion() {
        authenticateCitizen("privacy-stale");
        TenantContext.setOrganizationId(DemoOrganization.ID);
        assertThatThrownBy(() -> caseService.create(new CreateCaseRequest(
                        CatalogIds.VISA_TOURISM_FR_GW,
                        new ApplicantRequest("stale@example.com", "X", Map.of("nationality", "PT", "passportValidityMonths", 12)),
                        new PrivacyConsentAcceptance(true, "fr", "0"))))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getMessageKey())
                .isEqualTo("error.privacy.notice_stale");
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
