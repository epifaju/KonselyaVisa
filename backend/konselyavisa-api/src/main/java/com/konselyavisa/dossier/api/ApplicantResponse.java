package com.konselyavisa.dossier.api;

import java.util.Map;
import java.util.UUID;

public record ApplicantResponse(
        UUID id, String email, String displayName, String keycloakSubject, Map<String, Object> facts) {}
