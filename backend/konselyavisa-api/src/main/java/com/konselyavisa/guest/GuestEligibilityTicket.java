package com.konselyavisa.guest;

import java.util.Map;
import java.util.UUID;

public record GuestEligibilityTicket(
        UUID id, UUID organizationId, UUID procedureDefinitionId, Map<String, Object> facts) {}
