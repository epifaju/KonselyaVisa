package com.konselyavisa.identity;

import java.util.UUID;

public interface KeycloakCitizenDirectory {

    KeycloakCitizenCreated createCitizen(String email, String password, String displayName, UUID organizationId);
}
