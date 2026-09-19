package com.konselyavisa.appointment.api;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record BookAppointmentRequest(@NotNull UUID slotId) {}
