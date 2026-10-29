package com.konselyavisa.identity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

class CaseCreatorSnapshotTest {

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void roleFollowsTheMostSpecificWorkspaceRole() {
        assertThat(role("ROLE_COMPANY_USER", "ROLE_BUSINESS_ADMIN")).isEqualTo("COMPANY_ADMIN");
        assertThat(role("ROLE_COMPANY_USER", "ROLE_PLATFORM_ADMIN")).isEqualTo("COMPANY_ADMIN");
        assertThat(role("ROLE_COMPANY_USER")).isEqualTo("COMPANY_USER");
        assertThat(role("ROLE_PLATFORM_ADMIN")).isEqualTo("PLATFORM_ADMIN");
        assertThat(role("ROLE_BUSINESS_ADMIN")).isEqualTo("BUSINESS_ADMIN");
        assertThat(role("ROLE_SUPERVISOR")).isEqualTo("SUPERVISOR");
        assertThat(role("ROLE_AGENT")).isEqualTo("AGENT");
        assertThat(role("ROLE_CITIZEN")).isEqualTo("CITIZEN");
        assertThat(role()).isEqualTo("UNKNOWN");
    }

    @Test
    void labelPrefersGivenAndFamilyNames() {
        authenticate(jwt(claims -> {
            claims.claim("given_name", "Awa");
            claims.claim("family_name", "Diallo");
            claims.claim("name", "Ignored");
        }));
        assertThat(CaseCreatorSnapshot.label()).isEqualTo("Awa Diallo");
    }

    @Test
    void labelFallsBackThroughNameUsernameAndSubject() {
        authenticate(jwt(claims -> claims.claim("name", "Awa Diallo")));
        assertThat(CaseCreatorSnapshot.label()).isEqualTo("Awa Diallo");

        authenticate(jwt(claims -> claims.claim("preferred_username", "awa")));
        assertThat(CaseCreatorSnapshot.label()).isEqualTo("awa");

        authenticate(jwt(claims -> claims.subject("kc-sub")));
        assertThat(CaseCreatorSnapshot.label()).isEqualTo("kc-sub");
    }

    @Test
    void blankNameClaimsAreSkipped() {
        authenticate(jwt(claims -> {
            claims.claim("given_name", " ");
            claims.claim("family_name", "Diallo");
            claims.claim("name", " ");
            claims.claim("preferred_username", "awa");
        }));
        assertThat(CaseCreatorSnapshot.label()).isEqualTo("awa");
    }

    @Test
    void labelReadsAJwtPrincipalAndAPlainUsername() {
        Jwt jwt = baseBuilder().claim("name", "From principal").build();
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(jwt, null));
        assertThat(CaseCreatorSnapshot.label()).isEqualTo("From principal");

        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken("plain-user", "n/a"));
        assertThat(CaseCreatorSnapshot.label()).isEqualTo("plain-user");

        SecurityContextHolder.clearContext();
        assertThat(CaseCreatorSnapshot.label()).isEqualTo("unknown");
    }

    private static String role(String... authorities) {
        authenticate(jwt(builder -> builder.subject("user")), authorities);
        return CaseCreatorSnapshot.role();
    }

    private static void authenticate(Jwt jwt, String... authorities) {
        SecurityContextHolder.getContext()
                .setAuthentication(new JwtAuthenticationToken(
                        jwt, java.util.Arrays.stream(authorities).map(SimpleGrantedAuthority::new).toList()));
    }

    private static Jwt.Builder baseBuilder() {
        return Jwt.withTokenValue("test")
                .header("alg", "none")
                .subject("kc-sub")
                .issuedAt(Instant.parse("2026-01-01T00:00:00Z"))
                .expiresAt(Instant.parse("2026-01-01T01:00:00Z"));
    }

    private static Jwt jwt(java.util.function.Consumer<Jwt.Builder> customizer) {
        Jwt.Builder builder = baseBuilder();
        customizer.accept(builder);
        return builder.build();
    }
}
