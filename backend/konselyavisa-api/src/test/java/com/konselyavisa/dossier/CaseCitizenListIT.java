package com.konselyavisa.dossier;

import static org.assertj.core.api.Assertions.assertThat;

import com.konselyavisa.catalog.CatalogIds;
import com.konselyavisa.common.api.PageResponse;
import com.konselyavisa.dossier.api.ApplicantRequest;
import com.konselyavisa.dossier.api.CaseResponse;
import com.konselyavisa.dossier.api.CreateCaseRequest;
import com.konselyavisa.organization.DemoOrganization;
import com.konselyavisa.tenancy.TenantContext;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
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
class CaseCitizenListIT {

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

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        TenantContext.clear();
    }

    @Test
    void citizenListsOnlyOwnCases() {
        String subject = "citizen-list-subject";
        authenticateCitizen(subject);
        TenantContext.setOrganizationId(DemoOrganization.ID);

        CaseResponse created = caseService.create(new CreateCaseRequest(
                CatalogIds.VISA_TOURISM_FR_GW,
                new ApplicantRequest(
                        "citizen.list@example.com",
                        "Camara",
                        Map.of("nationality", "PT", "passportValidityMonths", 12))));

        PageResponse<CaseResponse> page = caseService.list(PageRequest.of(0, 20));
        assertThat(page.content()).extracting(CaseResponse::id).contains(created.id());
        assertThat(page.content()).extracting(CaseResponse::procedureCode).contains("VISA_TOURISM_FR_GW");
        CaseResponse listed = page.content().stream().filter(item -> item.id().equals(created.id())).findFirst().orElseThrow();
        assertThat(listed.createdAt()).isNotNull();
        assertThat(listed.updatedAt()).isNotNull();
        assertThat(listed.procedureNameI18n()).containsKeys("fr", "pt", "en");
        assertThat(listed.destinationCountry().isoCode()).isEqualTo("GW");
        assertThat(listed.nextAction()).isEqualTo(CaseNextAction.UPLOAD_DOCUMENTS);
        assertThat(listed.nextActionMessageKey()).isEqualTo("case.next_action.UPLOAD_DOCUMENTS");
        assertThat(listed.listUrgencyGroup()).isEqualTo(CaseListUrgencyGroup.ACTION_REQUIRED);
        assertThat(listed.listActionMessageKey()).isEqualTo("case.list_action.UPLOAD_DOCUMENTS");
        assertThat(listed.listSubtitleMessageKey()).isEqualTo("case.list_subtitle.UPLOAD_DOCUMENTS");
        assertThat(listed.estimatedInstructionDays()).isEqualTo(5);
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
