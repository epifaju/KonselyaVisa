package com.konselyavisa.outbox;

import com.konselyavisa.outbox.domain.OutboxEvent;
import com.konselyavisa.outbox.domain.OutboxEventTypes;
import com.konselyavisa.outbox.domain.OutboxStatus;
import com.konselyavisa.outbox.persistence.OutboxEventRepository;
import com.konselyavisa.tenancy.TenantContext;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class OutboxAppender {

    private final OutboxEventRepository outboxEventRepository;

    public OutboxAppender(OutboxEventRepository outboxEventRepository) {
        this.outboxEventRepository = outboxEventRepository;
    }

    public OutboxEvent append(String aggregateType, UUID aggregateId, String eventType, Map<String, Object> payload) {
        OutboxEvent event = new OutboxEvent();
        event.setAggregateType(aggregateType);
        event.setAggregateId(aggregateId);
        event.setEventType(eventType);
        event.setPayload(payload == null ? new LinkedHashMap<>() : new LinkedHashMap<>(payload));
        event.setStatus(OutboxStatus.PENDING);
        event.setAttempts(0);
        event.setNextAttemptAt(Instant.now());
        event.setOrganizationId(TenantContext.getOrganizationId());
        return outboxEventRepository.saveAndFlush(event);
    }

    public OutboxEvent appendCaseCreated(UUID caseId, Map<String, Object> payload) {
        return append(OutboxEventTypes.AGGREGATE_CASE, caseId, OutboxEventTypes.CASE_CREATED, payload);
    }

    public OutboxEvent appendDocumentUploaded(UUID caseId, Map<String, Object> payload) {
        return append(OutboxEventTypes.AGGREGATE_CASE, caseId, OutboxEventTypes.DOCUMENT_UPLOADED, payload);
    }

    public OutboxEvent appendPaymentCompleted(UUID caseId, Map<String, Object> payload) {
        return append(OutboxEventTypes.AGGREGATE_CASE, caseId, OutboxEventTypes.PAYMENT_COMPLETED, payload);
    }

    public OutboxEvent appendAppointmentBooked(UUID caseId, Map<String, Object> payload) {
        return append(OutboxEventTypes.AGGREGATE_CASE, caseId, OutboxEventTypes.APPOINTMENT_BOOKED, payload);
    }

    public OutboxEvent appendAppointmentCancelled(UUID caseId, Map<String, Object> payload) {
        return append(OutboxEventTypes.AGGREGATE_CASE, caseId, OutboxEventTypes.APPOINTMENT_CANCELLED, payload);
    }

    public OutboxEvent appendCorrectionRequested(UUID caseId, Map<String, Object> payload) {
        return append(OutboxEventTypes.AGGREGATE_CASE, caseId, OutboxEventTypes.CORRECTION_REQUESTED, payload);
    }

    public OutboxEvent appendDataDeletionRequested(UUID requestId, Map<String, Object> payload) {
        return append(OutboxEventTypes.AGGREGATE_PRIVACY, requestId, OutboxEventTypes.DATA_DELETION_REQUESTED, payload);
    }
}
