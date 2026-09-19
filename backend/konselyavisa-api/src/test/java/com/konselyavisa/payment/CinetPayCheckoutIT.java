package com.konselyavisa.payment;

import static org.assertj.core.api.Assertions.assertThat;

import com.konselyavisa.catalog.CatalogIds;
import com.konselyavisa.dossier.CaseService;
import com.konselyavisa.dossier.api.ApplicantRequest;
import com.konselyavisa.dossier.api.CaseResponse;
import com.konselyavisa.dossier.api.CreateCaseRequest;
import com.konselyavisa.organization.DemoOrganization;
import com.konselyavisa.organization.domain.Organization;
import com.konselyavisa.organization.persistence.OrganizationRepository;
import com.konselyavisa.outbox.domain.OutboxEvent;
import com.konselyavisa.outbox.domain.OutboxEventTypes;
import com.konselyavisa.outbox.persistence.OutboxEventRepository;
import com.konselyavisa.payment.api.OrderResponse;
import com.konselyavisa.payment.cinetpay.CinetPayGateway;
import com.konselyavisa.payment.cinetpay.CinetPayHmac;
import com.konselyavisa.payment.cinetpay.CinetPayHmacTest;
import com.konselyavisa.payment.cinetpay.FakeCinetPayGateway;
import com.konselyavisa.payment.domain.OrderStatus;
import com.konselyavisa.payment.domain.PaymentStatus;
import com.konselyavisa.payment.provider.PaymentProviderCodes;
import com.konselyavisa.payment.provider.WebhookPayload;
import com.konselyavisa.tenancy.TenantContext;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
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
class CinetPayCheckoutIT {

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

    @TestConfiguration
    static class FakeCinetPayConfig {
        @Bean
        @Primary
        CinetPayGateway cinetPayGateway() {
            return new FakeCinetPayGateway();
        }
    }

    @Autowired
    private CaseService caseService;

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private CinetPayGateway cinetPayGateway;

    @BeforeEach
    void enableCinetPay() {
        TenantContext.setOrganizationId(DemoOrganization.ID);
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            Organization organization = organizationRepository.findById(DemoOrganization.ID).orElseThrow();
            Map<String, Object> settings = new HashMap<>(organization.getSettings().getSettings());
            settings.put("paymentProvider", PaymentProviderCodes.CINETPAY);
            organization.getSettings().setSettings(settings);
        });
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void cinetPayWebhookIsIdempotentAndDoesNotCallN8nForBusinessRules() {
        TenantContext.setOrganizationId(DemoOrganization.ID);
        CaseResponse created = caseService.create(new CreateCaseRequest(
                CatalogIds.VISA_TOURISM_FR_GW,
                new ApplicantRequest(
                        "cinetpay@example.com", "Awa", Map.of("nationality", "PT", "passportValidityMonths", 12))));

        OrderResponse order = paymentService.createCheckout(created.id());
        assertThat(order.providerCode()).isEqualTo(PaymentProviderCodes.CINETPAY);
        assertThat(order.payment().checkoutUrl()).startsWith("https://checkout.cinetpay.com/");

        String transactionId = order.payment().providerReference();
        Map<String, Object> fields = CinetPayHmacTest.notifyFields(transactionId);
        fields.put("cpm_result", "ACCEPTED");
        String signature = CinetPayHmac.sign(fields, FakeCinetPayGateway.SECRET);
        fields.put("signature", signature);
        WebhookPayload payload = new WebhookPayload(fields, signature, null);
        paymentService.handleWebhook(PaymentProviderCodes.CINETPAY, payload);
        paymentService.handleWebhook(PaymentProviderCodes.CINETPAY, payload);

        TenantContext.setOrganizationId(DemoOrganization.ID);
        OrderResponse paid = paymentService.listByCase(created.id()).getFirst();
        assertThat(paid.status()).isEqualTo(OrderStatus.PAID);
        assertThat(paid.payment().status()).isEqualTo(PaymentStatus.COMPLETED);
        assertThat(((FakeCinetPayGateway) cinetPayGateway).checkTransaction(transactionId).status())
                .isEqualTo(PaymentStatus.COMPLETED);

        List<OutboxEvent> events = new TransactionTemplate(transactionManager)
                .execute(status -> outboxEventRepository.findByAggregateIdAndEventTypeOrderByCreatedAtAsc(
                        created.id(), OutboxEventTypes.PAYMENT_COMPLETED));
        assertThat(events).hasSize(1);
        assertThat(events.getFirst().getPayload()).containsEntry("providerCode", PaymentProviderCodes.CINETPAY);
    }
}
