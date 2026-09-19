package com.konselyavisa.identity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

class CurrentUserSubjectTest {

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void readsSubjectFromJwtAuthenticationToken() {
        Jwt jwt = Jwt.withTokenValue("test")
                .header("alg", "none")
                .subject("kc-sub-123")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .claim("preferred_username", "citizen.dev")
                .build();
        SecurityContextHolder.getContext()
                .setAuthentication(new JwtAuthenticationToken(
                        jwt, List.of(new SimpleGrantedAuthority("ROLE_CITIZEN"))));

        assertThat(CurrentUser.subject()).isEqualTo("kc-sub-123");
        assertThat(CurrentUser.isSelfScoped()).isTrue();
        assertThat(CurrentUser.isCompanyWorkspace()).isFalse();
    }

    @Test
    void companyCollaboratorIsNotCitizenScoped() {
        Jwt jwt = Jwt.withTokenValue("test")
                .header("alg", "none")
                .subject("kc-sub-company")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .claim("preferred_username", "company.dev")
                .build();
        SecurityContextHolder.getContext()
                .setAuthentication(new JwtAuthenticationToken(
                        jwt, List.of(new SimpleGrantedAuthority("ROLE_COMPANY_USER"))));

        assertThat(CurrentUser.isCitizenScoped()).isFalse();
        assertThat(CurrentUser.isCompanyCollaborator()).isTrue();
        assertThat(CurrentUser.username()).isEqualTo("company.dev");
    }
}
