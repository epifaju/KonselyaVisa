package com.konselyavisa.guest;

import java.util.UUID;

public record GuestEligibilityEvaluateResponse(boolean eligible, UUID ticketId) {}
