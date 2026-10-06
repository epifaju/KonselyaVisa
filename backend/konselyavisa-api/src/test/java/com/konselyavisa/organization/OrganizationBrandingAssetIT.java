package com.konselyavisa.organization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.konselyavisa.common.exception.BusinessException;
import com.konselyavisa.organization.api.OrganizationBrandingResponse;
import com.konselyavisa.organization.service.OrganizationBrandingAssetService;
import com.konselyavisa.organization.service.OrganizationBrandingService;
import com.konselyavisa.tenancy.TenantContext;
import java.util.List;
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
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
@ActiveProfiles("test")
class OrganizationBrandingAssetIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("konselyavisa")
            .withUsername("konselyavisa")
            .withPassword("konselyavisa");

    @Container
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
    private OrganizationBrandingAssetService brandingAssetService;

    @Autowired
    private OrganizationBrandingService organizationBrandingService;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        TenantContext.clear();
    }

    @Test
    void uploadAndLoadLogoThenClear() {
        authenticateAdmin();
        TenantContext.setOrganizationId(DemoOrganization.ID);

        assertThatThrownBy(() -> brandingAssetService.load(DemoOrganization.ID, OrganizationBrandingAssets.LOGO))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getMessageKey())
                .isEqualTo("error.organization.branding_missing");

        brandingAssetService.upload(
                OrganizationBrandingAssets.LOGO,
                new MockMultipartFile("file", "logo.png", "image/png", new byte[] {(byte) 0x89, 0x50, 0x4E, 0x47}));

        OrganizationBrandingResponse branding = organizationBrandingService.current();
        assertThat(branding.logoUrl()).contains("/api/v1/public/org/branding/logo");
        assertThat(branding.faviconUrl()).isNull();

        var asset = brandingAssetService.load(DemoOrganization.ID, OrganizationBrandingAssets.LOGO);
        assertThat(asset.contentType()).isEqualTo("image/png");
        assertThat(asset.bytes()).hasSize(4);

        brandingAssetService.delete(OrganizationBrandingAssets.LOGO);
        assertThat(organizationBrandingService.current().logoUrl()).isNull();
    }

    private static void authenticateAdmin() {
        Jwt jwt = Jwt.withTokenValue("test")
                .header("alg", "none")
                .subject("branding-admin")
                .claim("organization_id", DemoOrganization.ID.toString())
                .claim("preferred_username", "branding-admin")
                .build();
        SecurityContextHolder.getContext()
                .setAuthentication(new JwtAuthenticationToken(
                        jwt, List.of(new SimpleGrantedAuthority("ROLE_BUSINESS_ADMIN"))));
    }
}
