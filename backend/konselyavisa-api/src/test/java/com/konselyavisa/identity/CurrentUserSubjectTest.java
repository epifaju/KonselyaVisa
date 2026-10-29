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
        assertThat(CurrentUser.isCompanyOrgAdmin()).isFalse();
        assertThat(CurrentUser.username()).isEqualTo("company.dev");
    }

    @Test
    void anonymousAndMissingAuthenticationHaveNoSubject() {
        assertThat(CurrentUser.subject()).isNull();
        assertThat(CurrentUser.username()).isNull();
        assertThat(CurrentUser.hasRole("CITIZEN")).isFalse();
        assertThat(CurrentUser.hasRole("ROLE_CITIZEN")).isFalse();

        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken("anonymousUser", "n/a"));
        assertThat(CurrentUser.subject()).isNull();
        assertThat(CurrentUser.username()).isNull();
    }

    @Test
    void usernamePasswordAuthenticationUsesTheAccountName() {
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(
                        "ada", "n/a", List.of(new SimpleGrantedAuthority("ROLE_AGENT"))));

        assertThat(CurrentUser.subject()).isEqualTo("ada");
        assertThat(CurrentUser.username()).isEqualTo("ada");
        assertThat(CurrentUser.hasRole("AGENT")).isTrue();
        assertThat(CurrentUser.isSelfScoped()).isFalse();
    }

    @Test
    void blankJwtSubjectFallsBackToPreferredUsernameOnThePrincipal() {
        Jwt jwt = jwt("   ", "");
        SecurityContextHolder.getContext()
                .setAuthentication(new TestingAuthenticationToken(jwt, null, "ROLE_CITIZEN"));

        assertThat(CurrentUser.username()).isEqualTo("ada");
        assertThat(CurrentUser.subject()).isNotEqualTo("   ");
        assertThat(CurrentUser.isSelfScoped()).isTrue();
    }

    @Test
    void blankPreferredUsernameFallsBackToSubject() {
        Jwt jwt = Jwt.withTokenValue("test")
                .header("alg", "none")
                .subject("kc-sub")
                .issuedAt(Instant.parse("2026-01-01T00:00:00Z"))
                .expiresAt(Instant.parse("2026-01-01T01:00:00Z"))
                .claim("preferred_username", " ")
                .build();
        SecurityContextHolder.getContext()
                .setAuthentication(new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority("ROLE_CITIZEN"))));

        assertThat(CurrentUser.username()).isEqualTo("kc-sub");
    }

    @Test
    void companyOrgAdminSeesTheWorkspaceButNotASingleCollaboratorScope() {
        Jwt jwt = jwt("org-admin", "org.admin");
        SecurityContextHolder.getContext()
                .setAuthentication(new JwtAuthenticationToken(
                        jwt,
                        List.of(
                                new SimpleGrantedAuthority("ROLE_COMPANY_USER"),
                                new SimpleGrantedAuthority("ROLE_BUSINESS_ADMIN"))));

        assertThat(CurrentUser.isCompanyWorkspace()).isTrue();
        assertThat(CurrentUser.isCompanyOrgAdmin()).isTrue();
        assertThat(CurrentUser.isCompanyCollaborator()).isFalse();
        assertThat(CurrentUser.isCitizenScoped()).isFalse();
    }

    @Test
    void staffRoleCancelsCompanyWorkspace() {
        Jwt jwt = jwt("mixed", "mixed");
        SecurityContextHolder.getContext()
                .setAuthentication(new JwtAuthenticationToken(
                        jwt,
                        List.of(
                                new SimpleGrantedAuthority("ROLE_COMPANY_USER"),
                                new SimpleGrantedAuthority("ROLE_SUPERVISOR"))));

        assertThat(CurrentUser.isCompanyWorkspace()).isFalse();
        assertThat(CurrentUser.isCompanyCollaborator()).isFalse();
    }

    @Test
    void readsSubjectFromJwtPrincipalAndAcceptsPrefixedRoleChecks() {
        Jwt jwt = jwt("principal-sub", "ada");
        SecurityContextHolder.getContext()
                .setAuthentication(new TestingAuthenticationToken(jwt, null, "ROLE_CITIZEN"));

        assertThat(CurrentUser.subject()).isEqualTo("principal-sub");
        assertThat(CurrentUser.username()).isEqualTo("ada");
        assertThat(CurrentUser.hasRole("ROLE_CITIZEN")).isTrue();
    }

    @Test
    void extraStaffRolesRemoveCitizenAndCollaboratorScope() {
        for (String role : List.of(
                "ROLE_COMPANY_USER", "ROLE_AGENT", "ROLE_SUPERVISOR", "ROLE_BUSINESS_ADMIN", "ROLE_PLATFORM_ADMIN")) {
            authenticate(List.of("ROLE_CITIZEN", role));
            assertThat(CurrentUser.isCitizenScoped()).isFalse();
        }
        authenticate(List.of("ROLE_COMPANY_USER", "ROLE_AGENT"));
        assertThat(CurrentUser.isCompanyCollaborator()).isFalse();
        assertThat(CurrentUser.isCompanyOrgAdmin()).isFalse();

        authenticate(List.of("ROLE_COMPANY_USER", "ROLE_PLATFORM_ADMIN"));
        assertThat(CurrentUser.isCompanyCollaborator()).isFalse();
        assertThat(CurrentUser.isCompanyWorkspace()).isTrue();
        assertThat(CurrentUser.isCompanyOrgAdmin()).isTrue();
    }

    private static void authenticate(List<String> roles) {
        Jwt jwt = jwt("mixed", "mixed");
        SecurityContextHolder.getContext()
                .setAuthentication(new JwtAuthenticationToken(
                        jwt, roles.stream().map(SimpleGrantedAuthority::new).toList()));
    }

    private static Jwt jwt(String subject, String username) {
        return Jwt.withTokenValue("test")
                .header("alg", "none")
                .subject(subject)
                .issuedAt(Instant.parse("2026-01-01T00:00:00Z"))
                .expiresAt(Instant.parse("2026-01-01T01:00:00Z"))
                .claim("preferred_username", username.isBlank() ? "ada" : username)
                .build();
    }
}
