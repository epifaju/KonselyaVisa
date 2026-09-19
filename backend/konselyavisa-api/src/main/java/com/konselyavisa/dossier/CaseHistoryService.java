package com.konselyavisa.dossier;

import com.konselyavisa.dossier.api.CaseHistoryEventResponse;
import com.konselyavisa.outbox.domain.OutboxEvent;
import com.konselyavisa.outbox.domain.OutboxEventTypes;
import com.konselyavisa.outbox.persistence.OutboxEventRepository;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CaseHistoryService {

    private final CaseService caseService;
    private final OutboxEventRepository outboxEventRepository;

    public CaseHistoryService(CaseService caseService, OutboxEventRepository outboxEventRepository) {
        this.caseService = caseService;
        this.outboxEventRepository = outboxEventRepository;
    }

    @Transactional(readOnly = true)
    public List<CaseHistoryEventResponse> list(UUID caseId) {
        caseService.requireAccessible(caseId);
        return outboxEventRepository.findByAggregateIdOrderByCreatedAtAsc(caseId).stream()
                .map(CaseHistoryService::toResponse)
                .toList();
    }

    private static CaseHistoryEventResponse toResponse(OutboxEvent event) {
        Map<String, Object> payload = event.getPayload() == null ? Map.of() : event.getPayload();
        return new CaseHistoryEventResponse(
                event.getId(),
                event.getEventType(),
                event.getCreatedAt(),
                actorKind(event.getEventType()),
                stringValue(payload.get("messageKey")),
                stringValue(payload.get("requirementCode")));
    }

    private static String actorKind(String eventType) {
        if (OutboxEventTypes.CORRECTION_REQUESTED.equals(eventType)) {
            return "AGENT";
        }
        if (OutboxEventTypes.PAYMENT_COMPLETED.equals(eventType)
                || OutboxEventTypes.CASE_COMPLETED.equals(eventType)) {
            return "SYSTEM";
        }
        return "CITIZEN";
    }

    private static String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
