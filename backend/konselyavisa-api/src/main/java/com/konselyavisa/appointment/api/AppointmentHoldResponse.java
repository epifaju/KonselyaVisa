package com.konselyavisa.appointment.api;

import java.time.Instant;
import java.util.UUID;

public record AppointmentHoldResponse(UUID slotId, UUID caseId, Instant expiresAt) {}
