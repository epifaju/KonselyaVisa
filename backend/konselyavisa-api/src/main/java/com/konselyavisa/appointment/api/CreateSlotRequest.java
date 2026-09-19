package com.konselyavisa.appointment.api;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.Map;

public record CreateSlotRequest(
        @NotNull @Future Instant startsAt,
        @NotNull Instant endsAt,
        @Min(1) int capacity,
        @NotNull Map<String, String> locationI18n) {}
