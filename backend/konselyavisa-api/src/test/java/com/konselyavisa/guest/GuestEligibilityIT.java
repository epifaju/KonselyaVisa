package com.konselyavisa.guest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.konselyavisa.catalog.CatalogIds;
import com.konselyavisa.common.api.ApiResponse;
import com.konselyavisa.common.api.PageResponse;
import com.konselyavisa.common.exception.BusinessException;
import com.konselyavisa.dossier.CaseService;
import com.konselyavisa.dossier.api.ApplicantRequest;
import com.konselyavisa.dossier.api.CaseResponse;
import com.konselyavisa.dossier.api.CreateCaseRequest;
import com.konselyavisa.dossier.api.PrivacyConsentAcceptance;
import com.konselyavisa.organization.DemoOrganization;
import com.konselyavisa.tenancy.TenantContext;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers(disabledWithoutDocker = true)
@ActiveProfiles("test")
class GuestEligibilityIT {

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
    private TestRestTemplate restTemplate;

    @Autowired
    private CaseService caseService;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        TenantContext.clear();
    }

    @Test
    void publicCatalogAndTicketAttachAfterLogin() {
        ResponseEntity<PageResponse<Map<String, Object>>> catalog = restTemplate.exchange(
                "/api/v1/public/procedures",
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<>() {});
        assertThat(catalog.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(catalog.getBody()).isNotNull();
        assertThat(catalog.getBody().content()).isNotEmpty();

        GuestEligibilityEvaluateRequest eligibleRequest = new GuestEligibilityEvaluateRequest(
                DemoOrganization.ID,
                CatalogIds.VISA_TOURISM_FR_GW,
                Map.of("nationality", "PT", "passportValidityMonths", 12));
        ResponseEntity<ApiResponse<GuestEligibilityEvaluateResponse>> eligible = restTemplate.exchange(
                "/api/v1/public/eligibility-tickets",
                HttpMethod.POST,
                new HttpEntity<>(eligibleRequest),
                new ParameterizedTypeReference<>() {});
        assertThat(eligible.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(eligible.getBody()).isNotNull();
        assertThat(eligible.getBody().data().eligible()).isTrue();
        UUID ticketId = eligible.getBody().data().ticketId();
        assertThat(ticketId).isNotNull();

        GuestEligibilityEvaluateRequest ineligibleRequest = new GuestEligibilityEvaluateRequest(
                DemoOrganization.ID,
                CatalogIds.VISA_TOURISM_FR_GW,
                Map.of("nationality", "SN", "passportValidityMonths", 12));
        ResponseEntity<ApiResponse<GuestEligibilityEvaluateResponse>> ineligible = restTemplate.exchange(
                "/api/v1/public/eligibility-tickets",
                HttpMethod.POST,
                new HttpEntity<>(ineligibleRequest),
                new ParameterizedTypeReference<>() {});
        assertThat(ineligible.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(ineligible.getBody().data().eligible()).isFalse();
        assertThat(ineligible.getBody().data().ticketId()).isNull();

        TenantContext.setOrganizationId(DemoOrganization.ID);
        authenticate("guest-citizen", List.of("ROLE_CITIZEN"));
        CaseResponse created = caseService.create(new CreateCaseRequest(
                CatalogIds.VISA_TOURISM_FR_GW,
                new ApplicantRequest("guest@example.com", "Guest", Map.of()),
                PrivacyConsentAcceptance.currentAccepted("fr"),
                ticketId));
        assertThat(created.eligibilityPassed()).isTrue();
        assertThat(created.procedureDefinitionId()).isEqualTo(CatalogIds.VISA_TOURISM_FR_GW);

        assertThatThrownBy(() -> caseService.create(new CreateCaseRequest(
                        CatalogIds.VISA_TOURISM_FR_GW,
                        new ApplicantRequest("guest@example.com", "Guest", Map.of()),
                        PrivacyConsentAcceptance.currentAccepted("fr"),
                        ticketId)))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getMessageKey())
                .isEqualTo("error.guest.ticket_not_found");
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
