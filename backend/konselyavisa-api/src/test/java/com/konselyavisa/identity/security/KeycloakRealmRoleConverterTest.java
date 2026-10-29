package com.konselyavisa.identity.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

class KeycloakRealmRoleConverterTest {

    private final KeycloakRealmRoleConverter converter = new KeycloakRealmRoleConverter();

    @Test
    void prefixesRealmRoles() {
        Jwt jwt = jwtWithRealmAccess(Map.of("roles", List.of("CITIZEN", "AGENT")));

        assertThat(converter.convert(jwt))
                .containsExactlyInAnyOrder(
                        new SimpleGrantedAuthority("ROLE_CITIZEN"), new SimpleGrantedAuthority("ROLE_AGENT"));
    }

    @Test
    void collapsesDuplicateRolesAndDropsNonStrings() {
        Jwt jwt = jwtWithRealmAccess(Map.of("roles", List.of("CITIZEN", "CITIZEN", 12, "AGENT")));

        assertThat(converter.convert(jwt))
                .extracting(GrantedAuthority::getAuthority)
                .containsExactlyInAnyOrder("ROLE_CITIZEN", "ROLE_AGENT");
    }

    @Test
    void missingOrMalformedRealmAccessYieldsNoAuthorities() {
        assertThat(converter.convert(jwtWithRealmAccess(null))).isEmpty();
        assertThat(converter.convert(jwtWithRealmAccess("citizen"))).isEmpty();
        assertThat(converter.convert(jwtWithRealmAccess(Map.of("roles", "CITIZEN")))).isEmpty();
        assertThat(converter.convert(jwtWithRealmAccess(Map.of()))).isEmpty();
    }

    private static Jwt jwtWithRealmAccess(Object realmAccess) {
        Jwt.Builder builder = Jwt.withTokenValue("test")
                .header("alg", "none")
                .subject("user-1")
                .issuedAt(Instant.parse("2026-01-01T00:00:00Z"))
                .expiresAt(Instant.parse("2026-01-01T01:00:00Z"));
        if (realmAccess != null) {
            builder.claim("realm_access", realmAccess);
        }
        return builder.build();
    }
}
