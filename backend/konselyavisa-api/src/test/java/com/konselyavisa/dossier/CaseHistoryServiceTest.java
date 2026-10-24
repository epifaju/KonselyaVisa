package com.konselyavisa.dossier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.konselyavisa.dossier.api.CaseHistoryEventResponse;
import com.konselyavisa.outbox.domain.OutboxEvent;
import com.konselyavisa.outbox.domain.OutboxEventTypes;
import com.konselyavisa.outbox.persistence.OutboxEventRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CaseHistoryServiceTest {

    @Mock
    private CaseService caseService;

    @Mock
    private OutboxEventRepository outboxEventRepository;

    private CaseHistoryService service;

    @BeforeEach
    void setUp() {
        service = new CaseHistoryService(caseService, outboxEventRepository);
    }

    @Test
    void listDelegatesToRequireAccessible() {
        UUID caseId = UUID.randomUUID();
        when(outboxEventRepository.findByAggregateIdOrderByCreatedAtAsc(caseId)).thenReturn(List.of());

        assertThat(service.list(caseId)).isEmpty();
        verify(caseService).requireAccessible(caseId);
    }

    @Test
    void listMapsActorKindsAndPayloadFields() {
        UUID caseId = UUID.randomUUID();
        OutboxEvent correction = event(OutboxEventTypes.CORRECTION_REQUESTED, Map.of(
                "messageKey", "doc.blurry",
                "requirementCode", "PASSPORT"));
        OutboxEvent payment = event(OutboxEventTypes.PAYMENT_COMPLETED, Map.of());
        OutboxEvent completed = event(OutboxEventTypes.CASE_COMPLETED, null);
        OutboxEvent created = event(OutboxEventTypes.CASE_CREATED, Map.of("messageKey", 42));
        when(outboxEventRepository.findByAggregateIdOrderByCreatedAtAsc(caseId))
                .thenReturn(List.of(correction, payment, completed, created));

        List<CaseHistoryEventResponse> history = service.list(caseId);

        assertThat(history).hasSize(4);
        assertThat(history.get(0).actorKind()).isEqualTo("AGENT");
        assertThat(history.get(0).messageKey()).isEqualTo("doc.blurry");
        assertThat(history.get(0).requirementCode()).isEqualTo("PASSPORT");
        assertThat(history.get(1).actorKind()).isEqualTo("SYSTEM");
        assertThat(history.get(2).actorKind()).isEqualTo("SYSTEM");
        assertThat(history.get(2).messageKey()).isNull();
        assertThat(history.get(3).actorKind()).isEqualTo("CITIZEN");
        assertThat(history.get(3).messageKey()).isEqualTo("42");
    }

    private static OutboxEvent event(String type, Map<String, Object> payload) {
        OutboxEvent event = new OutboxEvent();
        event.setId(UUID.randomUUID());
        event.setEventType(type);
        event.setCreatedAt(Instant.parse("2026-10-24T10:00:00Z"));
        event.setPayload(payload);
        return event;
    }
}
