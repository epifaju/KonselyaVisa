package com.konselyavisa.identity;

import com.konselyavisa.common.exception.BusinessException;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class StubKeycloakCitizenDirectory implements KeycloakCitizenDirectory {

    private final Set<String> emails = ConcurrentHashMap.newKeySet();

    @Override
    public KeycloakCitizenCreated createCitizen(String email, String password, String displayName, UUID organizationId) {
        if (password == null || password.isBlank()) {
            throw BusinessException.badRequest("error.account.password_weak");
        }
        String normalized = email.trim().toLowerCase(Locale.ROOT);
        if (!emails.add(normalized)) {
            throw BusinessException.conflict("error.account.email_exists");
        }
        return new KeycloakCitizenCreated(UUID.randomUUID().toString(), normalized);
    }
}
