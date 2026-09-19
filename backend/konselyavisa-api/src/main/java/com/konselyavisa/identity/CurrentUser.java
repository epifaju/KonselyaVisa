package com.konselyavisa.identity;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

public final class CurrentUser {

    private CurrentUser() {}

    public static String subject() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return null;
        }
        if (authentication instanceof JwtAuthenticationToken jwtAuth) {
            String subject = jwtAuth.getToken().getSubject();
            if (subject != null && !subject.isBlank()) {
                return subject;
            }
        }
        if (authentication.getPrincipal() instanceof Jwt jwt) {
            String subject = jwt.getSubject();
            if (subject != null && !subject.isBlank()) {
                return subject;
            }
        }
        String name = authentication.getName();
        if (name == null || name.isBlank() || "anonymousUser".equals(name)) {
            return null;
        }
        return name;
    }

    public static boolean hasRole(String role) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return false;
        }
        String authority = role.startsWith("ROLE_") ? role : "ROLE_" + role;
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(authority::equals);
    }

    public static boolean isSelfScoped() {
        return isCitizenScoped();
    }

    public static boolean isCitizenScoped() {
        return hasRole("CITIZEN")
                && !hasRole("COMPANY_USER")
                && !hasRole("AGENT")
                && !hasRole("SUPERVISOR")
                && !hasRole("BUSINESS_ADMIN")
                && !hasRole("PLATFORM_ADMIN");
    }

    /** Mandataire: only cases they created. */
    public static boolean isCompanyCollaborator() {
        return hasRole("COMPANY_USER")
                && !hasRole("AGENT")
                && !hasRole("SUPERVISOR")
                && !hasRole("BUSINESS_ADMIN")
                && !hasRole("PLATFORM_ADMIN");
    }

    /** Company workspace including org admin who sees every collaborator file. */
    public static boolean isCompanyWorkspace() {
        return hasRole("COMPANY_USER") && !hasRole("AGENT") && !hasRole("SUPERVISOR");
    }

    public static boolean isCompanyOrgAdmin() {
        return isCompanyWorkspace() && (hasRole("BUSINESS_ADMIN") || hasRole("PLATFORM_ADMIN"));
    }

    public static String username() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthenticationToken jwtAuth) {
            String preferred = jwtAuth.getToken().getClaimAsString("preferred_username");
            if (preferred != null && !preferred.isBlank()) {
                return preferred;
            }
        }
        if (authentication != null && authentication.getPrincipal() instanceof Jwt jwt) {
            String preferred = jwt.getClaimAsString("preferred_username");
            if (preferred != null && !preferred.isBlank()) {
                return preferred;
            }
        }
        return subject();
    }
}
