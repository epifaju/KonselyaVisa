package com.konselyavisa.guest;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

public interface GuestEligibilityTicketStore {

    void save(GuestEligibilityTicket ticket, Duration ttl);

    Optional<GuestEligibilityTicket> find(UUID ticketId);

    Optional<GuestEligibilityTicket> consume(UUID ticketId);
}
