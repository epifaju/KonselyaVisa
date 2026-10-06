package com.konselyavisa.outbox;

import com.konselyavisa.organization.service.OutboxBrandingEnricher;
import com.konselyavisa.outbox.domain.OutboxEvent;
import com.konselyavisa.outbox.domain.OutboxStatus;
import com.konselyavisa.outbox.persistence.OutboxEventClaimer;
import com.konselyavisa.tenancy.TenantContext;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class OutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);

    private final OutboxProperties properties;
    private final OutboxEventClaimer claimer;
    private final OutboxWebhookClient webhookClient;
    private final OutboxBrandingEnricher brandingEnricher;
    private final TransactionTemplate transactionTemplate;

    public OutboxPublisher(
            OutboxProperties properties,
            OutboxEventClaimer claimer,
            OutboxWebhookClient webhookClient,
            OutboxBrandingEnricher brandingEnricher,
            PlatformTransactionManager transactionManager) {
        this.properties = properties;
        this.claimer = claimer;
        this.webhookClient = webhookClient;
        this.brandingEnricher = brandingEnricher;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public int publishDue() {
        if (!properties.hasWebhookUrl()) {
            return 0;
        }
        boolean previousAdmin = TenantContext.isPlatformAdmin();
        UUID previousOrg = TenantContext.getOrganizationId();
        TenantContext.setPlatformAdmin(true);
        TenantContext.setOrganizationId(null);
        try {
            Integer published = transactionTemplate.execute(status -> {
                List<OutboxEvent> due = claimer.lockDue(Instant.now(), properties.getBatchSize());
                int sent = 0;
                for (OutboxEvent event : due) {
                    if (dispatch(event)) {
                        sent++;
                    }
                }
                return sent;
            });
            return published == null ? 0 : published;
        } finally {
            TenantContext.setPlatformAdmin(previousAdmin);
            TenantContext.setOrganizationId(previousOrg);
        }
    }

    private boolean dispatch(OutboxEvent event) {
        try {
            Map<String, Object> payload =
                    new LinkedHashMap<>(event.getPayload() == null ? Map.of() : event.getPayload());
            Map<String, Object> branding = brandingEnricher.brandingFor(event.getOrganizationId());
            if (!branding.isEmpty()) {
                payload.put("branding", branding);
            }
            webhookClient.send(new OutboxWebhookMessage(
                    event.getId(),
                    event.getOrganizationId(),
                    event.getEventType(),
                    event.getAggregateType(),
                    event.getAggregateId(),
                    event.getCreatedAt(),
                    payload));
            event.setStatus(OutboxStatus.SENT);
            event.setSentAt(Instant.now());
            event.setLastError(null);
            return true;
        } catch (OutboxDispatchException ex) {
            int attempts = event.getAttempts() + 1;
            event.setAttempts(attempts);
            event.setLastError(trimError(ex.getMessage()));
            if (!ex.isRetryable() || attempts >= properties.getMaxAttempts()) {
                event.setStatus(OutboxStatus.FAILED);
                log.warn("Outbox event {} marked FAILED after {} attempt(s)", event.getId(), attempts);
            } else {
                event.setStatus(OutboxStatus.PENDING);
                event.setNextAttemptAt(Instant.now()
                        .plus(OutboxBackoff.delay(attempts, properties.getInitialBackoff(), properties.getMaxBackoff())));
            }
            return false;
        }
    }

    private static String trimError(String message) {
        if (message == null || message.isBlank()) {
            return "dispatch_failed";
        }
        return message.length() <= 200 ? message : message.substring(0, 200);
    }
}
