package com.konselyavisa.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.konselyavisa.outbox.domain.OutboxEvent;
import com.konselyavisa.outbox.domain.OutboxEventTypes;
import com.konselyavisa.outbox.domain.OutboxStatus;
import com.konselyavisa.outbox.persistence.OutboxEventRepository;
import com.konselyavisa.tenancy.TenantContext;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OutboxAppenderTest {

    @Mock
    private OutboxEventRepository outboxEventRepository;

    private OutboxAppender appender;

    @BeforeEach
    void setUp() {
        appender = new OutboxAppender(outboxEventRepository);
        when(outboxEventRepository.saveAndFlush(any(OutboxEvent.class))).thenAnswer(inv -> inv.getArgument(0));
        TenantContext.clear();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void appendSetsPendingStatusAttemptsAndOrganization() {
        UUID orgId = UUID.randomUUID();
        UUID aggregateId = UUID.randomUUID();
        TenantContext.setOrganizationId(orgId);

        OutboxEvent event = appender.append("CASE", aggregateId, "CASE_CREATED", Map.of("k", "v"));

        assertThat(event.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(event.getAttempts()).isZero();
        assertThat(event.getNextAttemptAt()).isNotNull();
        assertThat(event.getOrganizationId()).isEqualTo(orgId);
        assertThat(event.getPayload()).containsEntry("k", "v");
        assertThat(event.getPayload()).isNotSameAs(Map.of("k", "v"));
    }

    @Test
    void appendDefaultsNullPayloadToEmptyMap() {
        OutboxEvent event = appender.append("CASE", UUID.randomUUID(), "CASE_CREATED", null);

        assertThat(event.getPayload()).isEmpty();
        assertThat(event.getOrganizationId()).isNull();
    }

    @Test
    void appendCaseCreatedUsesCaseAggregate() {
        UUID caseId = UUID.randomUUID();

        appender.appendCaseCreated(caseId, Map.of("ref", "KV-1"));

        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getAggregateType()).isEqualTo(OutboxEventTypes.AGGREGATE_CASE);
        assertThat(captor.getValue().getAggregateId()).isEqualTo(caseId);
        assertThat(captor.getValue().getEventType()).isEqualTo(OutboxEventTypes.CASE_CREATED);
    }

    @Test
    void appendDataDeletionRequestedUsesPrivacyAggregate() {
        UUID requestId = UUID.randomUUID();

        appender.appendDataDeletionRequested(requestId, Map.of());

        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getAggregateType()).isEqualTo(OutboxEventTypes.AGGREGATE_PRIVACY);
        assertThat(captor.getValue().getEventType()).isEqualTo(OutboxEventTypes.DATA_DELETION_REQUESTED);
        assertThat(captor.getValue().getAggregateId()).isEqualTo(requestId);
    }

    @Test
    void appendAccountRegisteredUsesIdentityAggregate() {
        UUID identityId = UUID.randomUUID();

        appender.appendAccountRegistered(identityId, Map.of("ticketId", "t1"));

        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getAggregateType()).isEqualTo(OutboxEventTypes.AGGREGATE_IDENTITY);
        assertThat(captor.getValue().getEventType()).isEqualTo(OutboxEventTypes.ACCOUNT_REGISTERED);
    }

    @Test
    void appendWrappersUseExpectedEventTypes() {
        UUID caseId = UUID.randomUUID();
        UUID privacyId = UUID.randomUUID();

        appender.appendDocumentUploaded(caseId, Map.of());
        appender.appendPaymentCompleted(caseId, Map.of());
        appender.appendAppointmentBooked(caseId, Map.of());
        appender.appendAppointmentCancelled(caseId, Map.of());
        appender.appendCorrectionRequested(caseId, Map.of());
        appender.appendDataDeletionCompleted(privacyId, Map.of());

        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventRepository, org.mockito.Mockito.times(6)).saveAndFlush(captor.capture());
        assertThat(captor.getAllValues())
                .extracting(OutboxEvent::getEventType)
                .containsExactly(
                        OutboxEventTypes.DOCUMENT_UPLOADED,
                        OutboxEventTypes.PAYMENT_COMPLETED,
                        OutboxEventTypes.APPOINTMENT_BOOKED,
                        OutboxEventTypes.APPOINTMENT_CANCELLED,
                        OutboxEventTypes.CORRECTION_REQUESTED,
                        OutboxEventTypes.DATA_DELETION_COMPLETED);
    }
}
