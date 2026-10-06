package com.konselyavisa.organization;

import static org.assertj.core.api.Assertions.assertThat;

import com.konselyavisa.common.api.ApiResponse;
import com.konselyavisa.organization.api.OrganizationBrandingResponse;
import com.konselyavisa.organization.service.OrganizationBrandingService;
import com.konselyavisa.tenancy.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers(disabledWithoutDocker = true)
@ActiveProfiles("test")
class OrganizationBrandingIT {

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
    private OrganizationBrandingService organizationBrandingService;

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void publicOrgAndAuthenticatedBrandingUseCatalogNameNotI18nFallback() {
        ResponseEntity<ApiResponse<OrganizationBrandingResponse>> publicOrg = restTemplate.exchange(
                "/api/v1/public/org",
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<>() {});
        assertThat(publicOrg.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(publicOrg.getBody()).isNotNull();
        OrganizationBrandingResponse payload = publicOrg.getBody().data();
        assertThat(payload.organizationId()).isEqualTo(DemoOrganization.ID);
        assertThat(payload.nameI18n().get("fr")).contains("Guinée-Bissau");
        assertThat(payload.activeLanguages()).contains("fr", "pt", "en");
        assertThat(payload.brandColor()).isEqualTo("#0B5D3B");
        assertThat(payload.domain()).isEqualTo("visa.demo.konselya.local");

        TenantContext.setOrganizationId(DemoOrganization.ID);
        OrganizationBrandingResponse me = organizationBrandingService.current();
        assertThat(me.nameI18n()).isEqualTo(payload.nameI18n());
        assertThat(me.activeLanguages()).isEqualTo(payload.activeLanguages());
        assertThat(me.brandColor()).isEqualTo(payload.brandColor());
        assertThat(me.domain()).isEqualTo(payload.domain());
    }

    @Test
    void publicOrgResolvesOrganizationByCustomDomain() {
        ResponseEntity<ApiResponse<OrganizationBrandingResponse>> byDomain = restTemplate.exchange(
                "/api/v1/public/org?domain=visa.demo.konselya.local",
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<>() {});
        assertThat(byDomain.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(byDomain.getBody()).isNotNull();
        assertThat(byDomain.getBody().data().organizationId()).isEqualTo(DemoOrganization.ID);
        assertThat(byDomain.getBody().data().brandColor()).isEqualTo("#0B5D3B");
    }
}
