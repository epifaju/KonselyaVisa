package com.konselyavisa.guest;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryGuestEligibilityTicketStore implements GuestEligibilityTicketStore {

    private final Map<UUID, Entry> tickets = new ConcurrentHashMap<>();

    @Override
    public void save(GuestEligibilityTicket ticket, Duration ttl) {
        tickets.put(ticket.id(), new Entry(ticket, Instant.now().plus(ttl)));
    }

    @Override
    public Optional<GuestEligibilityTicket> consume(UUID ticketId) {
        Entry entry = tickets.remove(ticketId);
        if (entry == null || entry.expiresAt().isBefore(Instant.now())) {
            return Optional.empty();
        }
        return Optional.of(entry.ticket());
    }

    private record Entry(GuestEligibilityTicket ticket, Instant expiresAt) {}
}
