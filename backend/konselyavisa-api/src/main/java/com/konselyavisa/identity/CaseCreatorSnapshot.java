package com.konselyavisa.identity;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

public final class CaseCreatorSnapshot {

    private CaseCreatorSnapshot() {}

    public static String role() {
        if (CurrentUser.hasRole("COMPANY_USER")) {
            if (CurrentUser.hasRole("BUSINESS_ADMIN") || CurrentUser.hasRole("PLATFORM_ADMIN")) {
                return "COMPANY_ADMIN";
            }
            return "COMPANY_USER";
        }
        if (CurrentUser.hasRole("PLATFORM_ADMIN")) {
            return "PLATFORM_ADMIN";
        }
        if (CurrentUser.hasRole("BUSINESS_ADMIN")) {
            return "BUSINESS_ADMIN";
        }
        if (CurrentUser.hasRole("SUPERVISOR")) {
            return "SUPERVISOR";
        }
        if (CurrentUser.hasRole("AGENT")) {
            return "AGENT";
        }
        if (CurrentUser.hasRole("CITIZEN")) {
            return "CITIZEN";
        }
        return "UNKNOWN";
    }

    public static String label() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthenticationToken jwtAuth) {
            return labelFromJwt(jwtAuth.getToken());
        }
        if (authentication != null && authentication.getPrincipal() instanceof Jwt jwt) {
            return labelFromJwt(jwt);
        }
        String username = CurrentUser.username();
        return username == null || username.isBlank() ? "unknown" : username;
    }

    private static String labelFromJwt(Jwt jwt) {
        String given = jwt.getClaimAsString("given_name");
        String family = jwt.getClaimAsString("family_name");
        if (given != null && !given.isBlank() && family != null && !family.isBlank()) {
            return given + " " + family;
        }
        String name = jwt.getClaimAsString("name");
        if (name != null && !name.isBlank()) {
            return name;
        }
        String username = jwt.getClaimAsString("preferred_username");
        if (username != null && !username.isBlank()) {
            return username;
        }
        return jwt.getSubject() == null ? "unknown" : jwt.getSubject();
    }
}
