package com.konselyavisa.payment;

import static org.assertj.core.api.Assertions.assertThat;

import com.konselyavisa.catalog.CatalogIds;
import com.konselyavisa.dossier.CaseService;
import com.konselyavisa.dossier.api.ApplicantRequest;
import com.konselyavisa.dossier.api.CaseResponse;
import com.konselyavisa.dossier.api.CreateCaseRequest;
import com.konselyavisa.organization.DemoOrganization;
import com.konselyavisa.organization.FeatureFlagKeys;
import com.konselyavisa.organization.domain.FeatureFlag;
import com.konselyavisa.organization.domain.Organization;
import com.konselyavisa.organization.persistence.FeatureFlagRepository;
import com.konselyavisa.organization.persistence.OrganizationRepository;
import com.konselyavisa.payment.api.OrderResponse;
import com.konselyavisa.payment.provider.PaymentProviderCodes;
import com.konselyavisa.tenancy.TenantContext;
import java.util.HashMap;
import java.util.Map;
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
class PaymentProviderFlagIT {

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
    private PaymentService paymentService;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private FeatureFlagRepository featureFlagRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void stripeCheckoutFallsBackToMockWhenOrgFlagIsOff() {
        TenantContext.setOrganizationId(DemoOrganization.ID);
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            Organization organization = organizationRepository.findById(DemoOrganization.ID).orElseThrow();
            Map<String, Object> settings = new HashMap<>(organization.getSettings().getSettings());
            settings.put("paymentProvider", PaymentProviderCodes.STRIPE);
            organization.getSettings().setSettings(settings);
            FeatureFlag flag = featureFlagRepository
                    .findByKeyAndOrganizationId(
                            FeatureFlagKeys.paymentProvider(PaymentProviderCodes.STRIPE), DemoOrganization.ID)
                    .orElseThrow();
            flag.setEnabled(false);
        });

        CaseResponse created = caseService.create(new CreateCaseRequest(
                CatalogIds.VISA_TOURISM_FR_GW,
                new ApplicantRequest(
                        "flag-pay@example.com", "Awa", Map.of("nationality", "PT", "passportValidityMonths", 12))));
        OrderResponse order = paymentService.createCheckout(created.id());
        assertThat(order.providerCode()).isEqualTo(PaymentProviderCodes.MOCK);
    }
}
