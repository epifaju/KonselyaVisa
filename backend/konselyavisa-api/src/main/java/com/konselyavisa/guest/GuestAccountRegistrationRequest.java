package com.konselyavisa.guest;

import com.konselyavisa.dossier.api.PrivacyConsentAcceptance;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record GuestAccountRegistrationRequest(
        @NotNull UUID eligibilityTicketId,
        @NotBlank @Email @Size(max = 254) String email,
        @NotBlank @Size(max = 128) String password,
        @Size(max = 120) String displayName,
        @NotNull @Valid PrivacyConsentAcceptance privacyConsent) {}
