package com.konselyavisa.privacy.api;

import java.time.Instant;
import java.util.UUID;

public record PrivacyConsentResponse(
        UUID id, String purpose, String noticeVersion, String locale, Instant acceptedAt) {}
