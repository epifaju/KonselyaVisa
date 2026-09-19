package com.konselyavisa.dossier.api;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import java.util.Map;

public record ApplicantRequest(
        @Email String email, @Size(max = 200) String displayName, Map<String, Object> facts) {}
