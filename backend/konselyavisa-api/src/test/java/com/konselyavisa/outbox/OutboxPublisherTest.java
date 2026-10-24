package com.konselyavisa.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.konselyavisa.organization.service.OutboxBrandingEnricher;
import com.konselyavisa.outbox.domain.OutboxEvent;
import com.konselyavisa.outbox.domain.OutboxStatus;
import com.konselyavisa.outbox.persistence.OutboxEventClaimer;
import com.konselyavisa.tenancy.TenantContext;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

@ExtendWith(MockitoExtension.class)
class OutboxPublisherTest {

    @Mock
    private OutboxEventClaimer claimer;

    @Mock
    private OutboxWebhookClient webhookClient;

    @Mock
    private OutboxBrandingEnricher brandingEnricher;

    @Mock
    private PlatformTransactionManager transactionManager;

    private OutboxProperties properties;
    private OutboxPublisher publisher;

    @BeforeEach
    void setUp() {
        properties = new OutboxProperties();
        properties.setWebhookUrl("https://n8n.test/webhook");
        properties.setBatchSize(10);
        properties.setMaxAttempts(2);
        properties.setInitialBackoff(Duration.ofMillis(1));
        properties.setMaxBackoff(Duration.ofMillis(10));
        lenient().when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        lenient().when(brandingEnricher.brandingFor(any())).thenReturn(Map.of());
        publisher = new OutboxPublisher(properties, claimer, webhookClient, brandingEnricher, transactionManager);
        TenantContext.clear();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void publishDueReturnsZeroWithoutWebhookUrl() {
        properties.setWebhookUrl(" ");

        assertThat(publisher.publishDue()).isZero();
        verify(claimer, never()).lockDue(any(), anyInt());
    }

    @Test
    void publishDueMarksEventSentOnSuccess() {
        OutboxEvent event = pendingEvent();
        when(claimer.lockDue(any(Instant.class), eq(10))).thenReturn(List.of(event));

        assertThat(publisher.publishDue()).isEqualTo(1);
        assertThat(event.getStatus()).isEqualTo(OutboxStatus.SENT);
        assertThat(event.getSentAt()).isNotNull();
        assertThat(event.getLastError()).isNull();
        verify(webhookClient).send(any(OutboxWebhookMessage.class));
    }

    @Test
    void publishDueRetriesRetryableFailures() {
        OutboxEvent event = pendingEvent();
        when(claimer.lockDue(any(Instant.class), eq(10))).thenReturn(List.of(event));
        doThrow(new OutboxDispatchException("HTTP 503", true)).when(webhookClient).send(any());

        assertThat(publisher.publishDue()).isZero();
        assertThat(event.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(event.getAttempts()).isEqualTo(1);
        assertThat(event.getNextAttemptAt()).isAfter(Instant.now().minusSeconds(1));
        assertThat(event.getLastError()).isEqualTo("HTTP 503");
    }

    @Test
    void publishDueMarksFailedAtMaxAttempts() {
        OutboxEvent event = pendingEvent();
        event.setAttempts(1);
        when(claimer.lockDue(any(Instant.class), eq(10))).thenReturn(List.of(event));
        doThrow(new OutboxDispatchException("HTTP 500", true)).when(webhookClient).send(any());

        assertThat(publisher.publishDue()).isZero();
        assertThat(event.getStatus()).isEqualTo(OutboxStatus.FAILED);
        assertThat(event.getAttempts()).isEqualTo(2);
    }

    @Test
    void publishDueMarksFailedOnNonRetryableError() {
        OutboxEvent event = pendingEvent();
        when(claimer.lockDue(any(Instant.class), eq(10))).thenReturn(List.of(event));
        doThrow(new OutboxDispatchException("HTTP 400", false)).when(webhookClient).send(any());

        assertThat(publisher.publishDue()).isZero();
        assertThat(event.getStatus()).isEqualTo(OutboxStatus.FAILED);
        assertThat(event.getAttempts()).isEqualTo(1);
    }

    @Test
    void publishDueRestoresTenantContext() {
        UUID previousOrg = UUID.randomUUID();
        TenantContext.setOrganizationId(previousOrg);
        TenantContext.setPlatformAdmin(false);
        when(claimer.lockDue(any(Instant.class), anyInt())).thenReturn(List.of());

        publisher.publishDue();

        assertThat(TenantContext.getOrganizationId()).isEqualTo(previousOrg);
        assertThat(TenantContext.isPlatformAdmin()).isFalse();
    }

    private OutboxEvent pendingEvent() {
        OutboxEvent event = new OutboxEvent();
        event.setId(UUID.randomUUID());
        event.setOrganizationId(UUID.randomUUID());
        event.setAggregateType("CASE");
        event.setAggregateId(UUID.randomUUID());
        event.setEventType("CASE_CREATED");
        event.setPayload(Map.of("caseId", "x"));
        event.setStatus(OutboxStatus.PENDING);
        event.setAttempts(0);
        event.setNextAttemptAt(Instant.now());
        event.setCreatedAt(Instant.now());
        return event;
    }
}
