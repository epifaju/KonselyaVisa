package com.konselyavisa.guest;

import jakarta.validation.constraints.NotNull;
import java.util.Map;
import java.util.UUID;

public record GuestEligibilityEvaluateRequest(
        UUID organizationId, @NotNull UUID procedureDefinitionId, Map<String, Object> facts) {}
