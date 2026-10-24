package com.konselyavisa.guest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.konselyavisa.common.exception.BusinessException;
import com.konselyavisa.dossier.api.PrivacyConsentAcceptance;
import com.konselyavisa.identity.KeycloakCitizenCreated;
import com.konselyavisa.identity.KeycloakCitizenDirectory;
import com.konselyavisa.outbox.OutboxAppender;
import com.konselyavisa.privacy.PrivacyConsentService;
import com.konselyavisa.tenancy.TenantContext;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

@ExtendWith(MockitoExtension.class)
class GuestAccountRegistrationServiceTest {

    @Mock
    private GuestEligibilityTicketStore ticketStore;

    @Mock
    private GuestEligibilityService guestEligibilityService;

    @Mock
    private KeycloakCitizenDirectory keycloakCitizenDirectory;

    @Mock
    private PrivacyConsentService privacyConsentService;

    @Mock
    private OutboxAppender outboxAppender;

    @Mock
    private PlatformTransactionManager transactionManager;

    private GuestAccountRegistrationService service;

    @BeforeEach
    void setUp() {
        lenient().when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        service = new GuestAccountRegistrationService(
                ticketStore,
                guestEligibilityService,
                keycloakCitizenDirectory,
                privacyConsentService,
                outboxAppender,
                transactionManager);
        TenantContext.clear();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void registerThrowsWhenTicketMissing() {
        UUID ticketId = UUID.randomUUID();
        when(ticketStore.find(ticketId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.register(request(ticketId, "Citizen@Example.COM", "StrongPass123!")))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getMessageKey())
                .isEqualTo("error.guest.ticket_not_found");
        verify(keycloakCitizenDirectory, never()).createCitizen(any(), any(), any(), any());
    }

    @Test
    void registerRejectsWeakPassword() {
        GuestEligibilityTicket ticket = ticket();
        when(ticketStore.find(ticket.id())).thenReturn(Optional.of(ticket));

        assertThatThrownBy(() -> service.register(request(ticket.id(), "citizen@example.com", "short")))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getMessageKey())
                .isEqualTo("error.account.password_weak");
        verify(keycloakCitizenDirectory, never()).createCitizen(any(), any(), any(), any());
    }

    @Test
    void registerNormalizesEmailAndCreatesCitizen() {
        GuestEligibilityTicket ticket = ticket();
        when(ticketStore.find(ticket.id())).thenReturn(Optional.of(ticket));
        when(keycloakCitizenDirectory.createCitizen(
                        eq("citizen@example.com"), eq("StrongPass123!"), isNull(), eq(ticket.organizationId())))
                .thenReturn(new KeycloakCitizenCreated(UUID.randomUUID().toString(), "citizen@example.com"));

        assertThat(service.register(request(ticket.id(), "  Citizen@Example.COM ", "StrongPass123!")).created())
                .isTrue();
        verify(guestEligibilityService).bindOrganization(ticket.organizationId());
    }

    @Test
    void registerRecordsConsentAndOutboxInTransaction() {
        GuestEligibilityTicket ticket = ticket();
        UUID subject = UUID.randomUUID();
        PrivacyConsentAcceptance consent = PrivacyConsentAcceptance.currentAccepted("fr");
        when(ticketStore.find(ticket.id())).thenReturn(Optional.of(ticket));
        when(keycloakCitizenDirectory.createCitizen(
                        eq("citizen@example.com"), eq("StrongPass123!"), eq("Ada"), eq(ticket.organizationId())))
                .thenReturn(new KeycloakCitizenCreated(subject.toString(), "citizen@example.com"));

        GuestAccountRegistrationRequest request = new GuestAccountRegistrationRequest(
                ticket.id(), "citizen@example.com", "StrongPass123!", " Ada ", consent);
        assertThat(service.register(request).created()).isTrue();

        verify(privacyConsentService).recordAccountCreation(subject.toString(), consent);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> payload = ArgumentCaptor.forClass(Map.class);
        verify(outboxAppender).appendAccountRegistered(eq(subject), payload.capture());
        assertThat(payload.getValue()).containsEntry("ticketId", ticket.id().toString());
    }

    @Test
    void registerUsesTicketIdWhenKeycloakSubjectNotUuid() {
        GuestEligibilityTicket ticket = ticket();
        when(ticketStore.find(ticket.id())).thenReturn(Optional.of(ticket));
        when(keycloakCitizenDirectory.createCitizen(any(), any(), any(), any()))
                .thenReturn(new KeycloakCitizenCreated("not-a-uuid", "citizen@example.com"));

        service.register(request(ticket.id(), "citizen@example.com", "StrongPass123!"));

        verify(outboxAppender).appendAccountRegistered(eq(ticket.id()), any());
    }

    private static GuestEligibilityTicket ticket() {
        return new GuestEligibilityTicket(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), Map.of("age", 30));
    }

    private static GuestAccountRegistrationRequest request(UUID ticketId, String email, String password) {
        return new GuestAccountRegistrationRequest(
                ticketId, email, password, null, PrivacyConsentAcceptance.currentAccepted("fr"));
    }
}
