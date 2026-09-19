package com.konselyavisa.dossier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.konselyavisa.catalog.CatalogIds;
import com.konselyavisa.common.exception.BusinessException;
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
class CompanyCaseVisibilityIT {

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
    private CompanyCaseService companyCaseService;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        TenantContext.clear();
    }

    @Test
    void collaboratorSeesOnlyOwnFilesAndAdminSeesAllCompanyFiles() {
        TenantContext.setOrganizationId(DemoOrganization.ID);
        authenticate("company.alice", List.of("ROLE_COMPANY_USER"));
        CaseResponse aliceCase = createVisa("alice.client@example.com", "Alice Client");

        authenticate("company.bob", List.of("ROLE_COMPANY_USER"));
        CaseResponse bobCase = createVisa("bob.client@example.com", "Bob Client");

        assertThat(caseService.list(PageRequest.of(0, 20)).content())
                .extracting(CaseResponse::id)
                .contains(bobCase.id())
                .doesNotContain(aliceCase.id());
        assertThatThrownBy(() -> caseService.getById(aliceCase.id()))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getMessageKey())
                .isEqualTo("error.access.denied");
        assertThat(companyCaseService.summary().total()).isEqualTo(1);
        assertThat(companyCaseService.summary().byStatus().get(CaseStatus.CREATED)).isEqualTo(1L);

        authenticate("company.lead", List.of("ROLE_COMPANY_USER", "ROLE_BUSINESS_ADMIN"));
        assertThat(caseService.list(PageRequest.of(0, 20)).content())
                .extracting(CaseResponse::id)
                .contains(aliceCase.id(), bobCase.id());
        CaseResponse listed = caseService.getById(aliceCase.id());
        assertThat(listed.createdBy()).isEqualTo("company.alice");
        assertThat(listed.createdByRole()).isEqualTo("COMPANY_USER");
        assertThat(listed.createdByLabel()).isEqualTo("Alice Mandataire");
        assertThat(companyCaseService.summary().total()).isGreaterThanOrEqualTo(2);
    }

    private CaseResponse createVisa(String email, String displayName) {
        return caseService.create(new CreateCaseRequest(
                CatalogIds.VISA_TOURISM_FR_GW,
                new ApplicantRequest(email, displayName, Map.of("nationality", "PT", "passportValidityMonths", 12))));
    }

    private static void authenticate(String username, List<String> roles) {
        Jwt jwt = Jwt.withTokenValue("test")
                .header("alg", "none")
                .subject(username + "-sub")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300))
                .claim("preferred_username", username)
                .claim("given_name", username.contains("alice") ? "Alice" : username.contains("bob") ? "Bob" : "Lead")
                .claim("family_name", username.contains("alice") ? "Mandataire" : "Mandataire")
                .claim("organization_id", DemoOrganization.ID.toString())
                .build();
        SecurityContextHolder.getContext()
                .setAuthentication(new JwtAuthenticationToken(
                        jwt, roles.stream().map(SimpleGrantedAuthority::new).toList()));
        TenantContext.setOrganizationId(DemoOrganization.ID);
    }
}
