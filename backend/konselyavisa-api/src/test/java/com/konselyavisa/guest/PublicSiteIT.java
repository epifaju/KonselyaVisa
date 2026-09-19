package com.konselyavisa.guest;

import static org.assertj.core.api.Assertions.assertThat;

import com.konselyavisa.catalog.CatalogIds;
import com.konselyavisa.common.api.ApiResponse;
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
class PublicSiteIT {

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
    void publicSiteAndCaseLookupWithoutPassword() {
        ResponseEntity<ApiResponse<PublicSiteResponse>> site = restTemplate.exchange(
                "/api/v1/public/site",
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<>() {});
        assertThat(site.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(site.getBody()).isNotNull();
        PublicSiteResponse payload = site.getBody().data();
        assertThat(payload.organizationId()).isEqualTo(DemoOrganization.ID);
        assertThat(payload.nameI18n().get("fr")).contains("Guinée-Bissau");
        assertThat(payload.activeLanguages()).contains("fr", "pt", "en");
        assertThat(payload.addressI18n()).isNotEmpty();
        assertThat(payload.formalities()).extracting(PublicFormalityResponse::category).isNotEmpty();

        TenantContext.setOrganizationId(DemoOrganization.ID);
        authenticate("site-citizen", List.of("ROLE_CITIZEN"));
        CaseResponse created = caseService.create(new CreateCaseRequest(
                CatalogIds.VISA_TOURISM_FR_GW,
                new ApplicantRequest("lookup@example.com", "Lookup", Map.of("nationality", "PT", "passportValidityMonths", 12)),
                PrivacyConsentAcceptance.currentAccepted("fr")));

        ResponseEntity<ApiResponse<PublicCaseStatusResponse>> found = restTemplate.exchange(
                "/api/v1/public/case-status",
                HttpMethod.POST,
                new HttpEntity<>(new PublicCaseLookupRequest(
                        DemoOrganization.ID, created.reference(), "lookup@example.com", null)),
                new ParameterizedTypeReference<>() {});
        assertThat(found.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(found.getBody().data().reference()).isEqualTo(created.reference());

        ResponseEntity<ApiResponse<Void>> missing = restTemplate.exchange(
                "/api/v1/public/case-status",
                HttpMethod.POST,
                new HttpEntity<>(new PublicCaseLookupRequest(
                        DemoOrganization.ID, created.reference(), "other@example.com", null)),
                new ParameterizedTypeReference<>() {});
        assertThat(missing.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
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
