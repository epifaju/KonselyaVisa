package com.konselyavisa.guest;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class InMemoryGuestEligibilityTicketStoreTest {

    private InMemoryGuestEligibilityTicketStore store;

    @BeforeEach
    void setUp() {
        store = new InMemoryGuestEligibilityTicketStore();
    }

    @Test
    void saveAndFindReturnsTicketBeforeExpiry() {
        GuestEligibilityTicket ticket = ticket();
        store.save(ticket, Duration.ofMinutes(5));

        assertThat(store.find(ticket.id())).contains(ticket);
    }

    @Test
    void findReturnsEmptyAfterExpiryAndRemovesEntry() {
        GuestEligibilityTicket ticket = ticket();
        store.save(ticket, Duration.ofSeconds(-1));

        assertThat(store.find(ticket.id())).isEmpty();
        assertThat(store.find(ticket.id())).isEmpty();
    }

    @Test
    void consumeReturnsTicketAndRemovesIt() {
        GuestEligibilityTicket ticket = ticket();
        store.save(ticket, Duration.ofMinutes(5));

        assertThat(store.consume(ticket.id())).contains(ticket);
        assertThat(store.find(ticket.id())).isEmpty();
        assertThat(store.consume(ticket.id())).isEmpty();
    }

    @Test
    void consumeReturnsEmptyWhenExpired() {
        GuestEligibilityTicket ticket = ticket();
        store.save(ticket, Duration.ofSeconds(-1));

        assertThat(store.consume(ticket.id())).isEmpty();
    }

    @Test
    void consumeReturnsEmptyWhenUnknownId() {
        assertThat(store.consume(UUID.randomUUID())).isEmpty();
    }

    private static GuestEligibilityTicket ticket() {
        return new GuestEligibilityTicket(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), Map.of("age", 30));
    }
}
