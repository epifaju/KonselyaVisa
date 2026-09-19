package com.konselyavisa.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.konselyavisa.catalog.CatalogIds;
import com.konselyavisa.common.exception.BusinessException;
import com.konselyavisa.dossier.CaseService;
import com.konselyavisa.dossier.api.ApplicantRequest;
import com.konselyavisa.dossier.api.CaseResponse;
import com.konselyavisa.dossier.api.CreateCaseRequest;
import com.konselyavisa.organization.DemoOrganization;
import com.konselyavisa.outbox.domain.OutboxEvent;
import com.konselyavisa.outbox.domain.OutboxEventTypes;
import com.konselyavisa.outbox.persistence.OutboxEventRepository;
import com.konselyavisa.payment.api.OrderResponse;
import com.konselyavisa.payment.domain.OrderStatus;
import com.konselyavisa.payment.domain.PaymentStatus;
import com.konselyavisa.tenancy.TenantContext;
import java.util.List;
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
class PaymentCheckoutIT {

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
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void checkoutFreezesCatalogPriceAndWebhookIsIdempotent() {
        TenantContext.setOrganizationId(DemoOrganization.ID);
        CaseResponse created = caseService.create(new CreateCaseRequest(
                CatalogIds.VISA_TOURISM_FR_GW,
                new ApplicantRequest(
                        "pay@example.com", "Awa", Map.of("nationality", "PT", "passportValidityMonths", 12))));

        OrderResponse order = paymentService.createCheckout(created.id());
        assertThat(order.amountMinor()).isEqualTo(8500L);
        assertThat(order.currency()).isEqualTo("EUR");
        assertThat(order.status()).isEqualTo(OrderStatus.PENDING_PAYMENT);
        assertThat(order.providerCode()).isEqualTo("MOCK");
        assertThat(order.payment().providerReference()).startsWith("mock_");

        Map<String, Object> webhook = Map.of(
                "providerReference",
                order.payment().providerReference(),
                "idempotencyKey",
                "evt-1",
                "event",
                "payment.completed");
        paymentService.handleWebhook("MOCK", webhook);
        paymentService.handleWebhook("MOCK", webhook);

        TenantContext.setOrganizationId(DemoOrganization.ID);
        OrderResponse paid = paymentService.listByCase(created.id()).getFirst();
        assertThat(paid.status()).isEqualTo(OrderStatus.PAID);
        assertThat(paid.payment().status()).isEqualTo(PaymentStatus.COMPLETED);

        TransactionTemplate template = new TransactionTemplate(transactionManager);
        List<OutboxEvent> events = template.execute(status ->
                outboxEventRepository.findByAggregateIdAndEventTypeOrderByCreatedAtAsc(
                        created.id(), OutboxEventTypes.PAYMENT_COMPLETED));
        assertThat(events).hasSize(1);
        assertThat(events.getFirst().getPayload())
                .containsEntry("orderId", order.id().toString())
                .containsEntry("paymentId", order.payment().id().toString())
                .containsEntry("currency", "EUR");
        assertThat(((Number) events.getFirst().getPayload().get("amountMinor")).longValue()).isEqualTo(8500L);

        assertThatThrownBy(() -> paymentService.createCheckout(created.id()))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getMessageKey())
                .isEqualTo("error.order.already_exists");
    }
}
