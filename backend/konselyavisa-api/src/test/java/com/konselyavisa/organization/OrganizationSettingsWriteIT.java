package com.konselyavisa.organization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.konselyavisa.common.exception.BusinessException;
import com.konselyavisa.organization.api.OrganizationResponse;
import com.konselyavisa.organization.api.UpdateOrganizationWhiteLabelRequest;
import com.konselyavisa.organization.service.OrganizationSettingsWriteService;
import com.konselyavisa.payment.provider.PaymentProviderCodes;
import com.konselyavisa.tenancy.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
@ActiveProfiles("test")
class OrganizationSettingsWriteIT {

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
    private OrganizationSettingsWriteService organizationSettingsWriteService;

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void updatesBrandColorDomainAndPaymentProviderWithoutWipingOtherKeys() {
        TenantContext.setOrganizationId(DemoOrganization.ID);

        OrganizationResponse updated = organizationSettingsWriteService.updateCurrent(
                new UpdateOrganizationWhiteLabelRequest("#123ABC", "visa.acme.example", PaymentProviderCodes.PAYDUNYA));

        assertThat(updated.settings()).containsEntry("brandColor", "#123ABC");
        assertThat(updated.settings()).containsEntry("domain", "visa.acme.example");
        assertThat(updated.settings()).containsEntry("paymentProvider", PaymentProviderCodes.PAYDUNYA);
        assertThat(updated.settings()).containsKey("activeLanguages");

        OrganizationResponse cleared = organizationSettingsWriteService.updateCurrent(
                new UpdateOrganizationWhiteLabelRequest("", "", PaymentProviderCodes.MOCK));
        assertThat(cleared.settings()).doesNotContainKey("brandColor");
        assertThat(cleared.settings()).doesNotContainKey("domain");
        assertThat(cleared.settings()).containsEntry("paymentProvider", PaymentProviderCodes.MOCK);
        assertThat(cleared.settings()).containsKey("activeLanguages");
    }

    @Test
    void rejectsInvalidBrandColor() {
        TenantContext.setOrganizationId(DemoOrganization.ID);
        assertThatThrownBy(() -> organizationSettingsWriteService.updateCurrent(
                        new UpdateOrganizationWhiteLabelRequest("not-a-color", null, null)))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getMessageKey())
                .isEqualTo("error.organization.brand_color_invalid");
    }
}
