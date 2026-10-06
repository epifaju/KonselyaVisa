package com.konselyavisa.guest;

import com.konselyavisa.common.exception.BusinessException;
import com.konselyavisa.identity.KeycloakCitizenCreated;
import com.konselyavisa.identity.KeycloakCitizenDirectory;
import com.konselyavisa.outbox.OutboxAppender;
import com.konselyavisa.privacy.PrivacyConsentService;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class GuestAccountRegistrationService {

    private final GuestEligibilityTicketStore ticketStore;
    private final GuestEligibilityService guestEligibilityService;
    private final KeycloakCitizenDirectory keycloakCitizenDirectory;
    private final PrivacyConsentService privacyConsentService;
    private final OutboxAppender outboxAppender;
    private final TransactionTemplate transactionTemplate;

    public GuestAccountRegistrationService(
            GuestEligibilityTicketStore ticketStore,
            GuestEligibilityService guestEligibilityService,
            KeycloakCitizenDirectory keycloakCitizenDirectory,
            PrivacyConsentService privacyConsentService,
            OutboxAppender outboxAppender,
            PlatformTransactionManager transactionManager) {
        this.ticketStore = ticketStore;
        this.guestEligibilityService = guestEligibilityService;
        this.keycloakCitizenDirectory = keycloakCitizenDirectory;
        this.privacyConsentService = privacyConsentService;
        this.outboxAppender = outboxAppender;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public GuestAccountRegistrationResponse register(GuestAccountRegistrationRequest request) {
        GuestEligibilityTicket ticket = ticketStore
                .find(request.eligibilityTicketId())
                .orElseThrow(() -> BusinessException.notFound("error.guest.ticket_not_found"));
        guestEligibilityService.bindOrganization(ticket.organizationId());
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        GuestAccountPasswordRules.requireStrong(request.password(), email);
        KeycloakCitizenCreated created = keycloakCitizenDirectory.createCitizen(
                email, request.password(), blankToNull(request.displayName()), ticket.organizationId());
        transactionTemplate.executeWithoutResult(status -> {
            privacyConsentService.recordAccountCreation(created.subject(), request.privacyConsent());
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("ticketId", ticket.id().toString());
            outboxAppender.appendAccountRegistered(parseUuid(created.subject(), ticket.id()), payload);
        });
        return new GuestAccountRegistrationResponse(true);
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private static UUID parseUuid(String subject, UUID fallback) {
        try {
            return UUID.fromString(subject);
        } catch (IllegalArgumentException ex) {
            return fallback;
        }
    }
}
