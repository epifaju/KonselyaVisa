package com.konselyavisa.outbox;

import static org.assertj.core.api.Assertions.assertThat;

import com.konselyavisa.outbox.domain.OutboxEvent;
import com.konselyavisa.outbox.domain.OutboxEventTypes;
import com.konselyavisa.outbox.domain.OutboxStatus;
import com.konselyavisa.outbox.persistence.OutboxEventRepository;
import com.konselyavisa.organization.DemoOrganization;
import com.konselyavisa.tenancy.TenantContext;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
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
class OutboxPublisherIT {

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
        registry.add("konselyavisa.outbox.enabled", () -> "false");
        registry.add("konselyavisa.outbox.initial-backoff", () -> "1ms");
        registry.add("konselyavisa.outbox.max-backoff", () -> "1ms");
        registry.add("konselyavisa.outbox.max-attempts", () -> "2");
        registry.add("konselyavisa.outbox.webhook-secret", () -> "test-secret");
    }

    @Autowired
    private OutboxAppender outboxAppender;

    @Autowired
    private OutboxPublisher publisher;

    @Autowired
    private OutboxProperties properties;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        properties.setWebhookUrl("");
    }

    @Test
    void publishesPendingEventAndRetriesThenSucceeds() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        AtomicReference<String> body = new AtomicReference<>();
        AtomicReference<String> signature = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/webhook", exchange -> {
            int n = calls.incrementAndGet();
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            signature.set(exchange.getRequestHeaders().getFirst("X-Konselya-Signature"));
            int status = n == 1 ? 503 : 200;
            exchange.sendResponseHeaders(status, -1);
            exchange.close();
        });
        server.start();
        try {
            properties.setWebhookUrl("http://127.0.0.1:" + server.getAddress().getPort() + "/webhook");
            TenantContext.setOrganizationId(DemoOrganization.ID);
            UUID caseId = UUID.randomUUID();
            OutboxEvent pending = outboxAppender.append(
                    OutboxEventTypes.AGGREGATE_CASE, caseId, OutboxEventTypes.CASE_CREATED, Map.of("caseId", caseId.toString()));

            assertThat(publisher.publishDue()).isZero();
            OutboxEvent afterFail = reload(pending.getId());
            assertThat(afterFail.getStatus()).isEqualTo(OutboxStatus.PENDING);
            assertThat(afterFail.getAttempts()).isEqualTo(1);

            Thread.sleep(5);
            assertThat(publisher.publishDue()).isEqualTo(1);
            OutboxEvent sent = reload(pending.getId());
            assertThat(sent.getStatus()).isEqualTo(OutboxStatus.SENT);
            assertThat(sent.getSentAt()).isNotNull();
            assertThat(body.get()).contains(caseId.toString());
            assertThat(signature.get()).startsWith("sha256=");
            assertThat(calls.get()).isEqualTo(2);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void marksFailedAfterMaxAttempts() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/webhook", exchange -> {
            exchange.sendResponseHeaders(500, -1);
            exchange.close();
        });
        server.start();
        try {
            properties.setWebhookUrl("http://127.0.0.1:" + server.getAddress().getPort() + "/webhook");
            TenantContext.setOrganizationId(DemoOrganization.ID);
            OutboxEvent pending = outboxAppender.append(
                    OutboxEventTypes.AGGREGATE_CASE,
                    UUID.randomUUID(),
                    OutboxEventTypes.CASE_CREATED,
                    Map.of("ref", "fail-me"));

            publisher.publishDue();
            Thread.sleep(5);
            publisher.publishDue();

            OutboxEvent failed = reload(pending.getId());
            assertThat(failed.getStatus()).isEqualTo(OutboxStatus.FAILED);
            assertThat(failed.getAttempts()).isEqualTo(2);
            assertThat(failed.getLastError()).contains("HTTP 500");
        } finally {
            server.stop(0);
        }
    }

    private OutboxEvent reload(UUID id) {
        TenantContext.setPlatformAdmin(true);
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        return template.execute(status -> outboxEventRepository.findById(id).orElseThrow());
    }
}
