package com.konselyavisa.privacy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.konselyavisa.common.exception.BusinessException;
import com.konselyavisa.organization.DemoOrganization;
import com.konselyavisa.privacy.api.AcceptOrganizationDpaRequest;
import com.konselyavisa.privacy.api.OrganizationDpaResponse;
import com.konselyavisa.privacy.domain.OrganizationDpaStatus;
import com.konselyavisa.tenancy.TenantContext;
import java.time.Instant;
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
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
@ActiveProfiles("test")
class OrganizationDpaIT {

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
    private OrganizationDpaService organizationDpaService;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        TenantContext.clear();
    }

    @Test
    void demoOrgHasSeededActiveDpa() {
        TenantContext.setOrganizationId(DemoOrganization.ID);
        OrganizationDpaResponse dpa = organizationDpaService.findActiveForCurrentOrg().orElseThrow();
        assertThat(dpa.version()).isEqualTo(OrganizationDpaCatalog.CURRENT_VERSION);
        assertThat(dpa.status()).isEqualTo(OrganizationDpaStatus.ACTIVE);
        assertThat(dpa.retentionDays()).isEqualTo(1825);
        assertThat(dpa.controllerNameI18n()).containsKey("fr");
        assertThat(dpa.summaryI18n()).containsKey("en");
    }

    @Test
    void businessAdminCanAcceptNewVersionAndSupersedePrevious() {
        authenticateAdmin("dpa-admin");
        TenantContext.setOrganizationId(DemoOrganization.ID);
        UUID previousId = organizationDpaService.findActiveForCurrentOrg().orElseThrow().id();

        OrganizationDpaResponse accepted = organizationDpaService.accept(new AcceptOrganizationDpaRequest(
                OrganizationDpaCatalog.CURRENT_VERSION,
                Map.of("fr", "Consulat démo mis à jour", "en", "Updated demo consulate"),
                "https://example.local/dpa.pdf"));
        assertThat(accepted.id()).isNotEqualTo(previousId);
        assertThat(accepted.acceptedByLabel()).isEqualTo("dpa-admin");
        assertThat(accepted.documentUri()).isEqualTo("https://example.local/dpa.pdf");
        assertThat(accepted.controllerNameI18n()).containsEntry("fr", "Consulat démo mis à jour");

        assertThatThrownBy(() -> organizationDpaService.accept(new AcceptOrganizationDpaRequest(
                        "1999.01", Map.of("fr", "X"), null)))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getMessageKey())
                .isEqualTo("error.privacy.dpa_version_stale");
    }

    @Test
    void orgWithoutDpaHasEmptyOptional() {
        TenantContext.setOrganizationId(UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"));
        assertThat(organizationDpaService.findActiveForCurrentOrg()).isEmpty();
    }

    private static void authenticateAdmin(String username) {
        Jwt jwt = Jwt.withTokenValue("test")
                .header("alg", "none")
                .subject(username)
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300))
                .claim("organization_id", DemoOrganization.ID.toString())
                .claim("preferred_username", username)
                .build();
        SecurityContextHolder.getContext()
                .setAuthentication(new JwtAuthenticationToken(
                        jwt, List.of(new SimpleGrantedAuthority("ROLE_BUSINESS_ADMIN"))));
    }
}
