package com.konselyavisa.guest;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record PublicCaseLookupRequest(
        UUID organizationId,
        @NotBlank @Size(max = 40) String reference,
        @Email @Size(max = 255) String email,
        @Size(max = 32) String dateOfBirth) {}
