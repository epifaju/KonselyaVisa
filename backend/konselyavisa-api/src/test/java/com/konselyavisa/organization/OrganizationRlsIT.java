package com.konselyavisa.organization;

import static org.assertj.core.api.Assertions.assertThat;

import com.konselyavisa.catalog.persistence.CountryRepository;
import com.konselyavisa.organization.domain.Organization;
import com.konselyavisa.organization.domain.OrganizationSettings;
import com.konselyavisa.organization.domain.OrganizationStatus;
import com.konselyavisa.organization.persistence.OrganizationRepository;
import com.konselyavisa.tenancy.TenantContext;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
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
class OrganizationRlsIT {

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
    private OrganizationRepository organizationRepository;

    @Autowired
    private CountryRepository countryRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void seedCountriesAreGlobal() {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        Long count = template.execute(status -> countryRepository.count());
        assertThat(count).isEqualTo(3);
    }

    @Test
    void organizationsHiddenWithoutTenantOrAdmin() {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.executeWithoutResult(status -> assertThat(organizationRepository.findAll()).isEmpty());
    }

    @Test
    void demoOrganizationVisibleForItsTenant() {
        TenantContext.setOrganizationId(DemoOrganization.ID);
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.executeWithoutResult(status -> assertThat(organizationRepository.findById(DemoOrganization.ID))
                .isPresent()
                .get()
                .extracting(Organization::getCode)
                .isEqualTo("DEMO"));
    }

    @Test
    void tenantCannotSeeForeignOrganization() {
        TenantContext.setPlatformAdmin(true);
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        UUID foreignId = template.execute(status -> {
            Organization organization = new Organization();
            organization.setCode("OTHER");
            organization.setSlug("other");
            organization.setNameI18n(Map.of("fr", "Autre", "pt", "Outra", "en", "Other"));
            organization.setStatus(OrganizationStatus.ACTIVE);
            OrganizationSettings settings = new OrganizationSettings();
            settings.setOrganization(organization);
            organization.setSettings(settings);
            return organizationRepository.save(organization).getId();
        });
        TenantContext.clear();

        TenantContext.setOrganizationId(DemoOrganization.ID);
        template.executeWithoutResult(status -> {
            assertThat(organizationRepository.findById(DemoOrganization.ID)).isPresent();
            assertThat(organizationRepository.findById(foreignId)).isEmpty();
        });
    }
}
