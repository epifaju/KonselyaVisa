package com.konselyavisa.appointment.api;

import com.konselyavisa.appointment.domain.AppointmentSlotStatus;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record AppointmentSlotResponse(
        UUID id,
        Instant startsAt,
        Instant endsAt,
        int capacity,
        int remainingCapacity,
        Map<String, String> locationI18n,
        AppointmentSlotStatus status) {}
