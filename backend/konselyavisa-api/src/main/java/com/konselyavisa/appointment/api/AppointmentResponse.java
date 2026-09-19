package com.konselyavisa.appointment.api;

import com.konselyavisa.appointment.domain.AppointmentStatus;
import java.util.UUID;

public record AppointmentResponse(
        UUID id,
        UUID caseId,
        AppointmentStatus status,
        AppointmentSlotResponse slot) {}
