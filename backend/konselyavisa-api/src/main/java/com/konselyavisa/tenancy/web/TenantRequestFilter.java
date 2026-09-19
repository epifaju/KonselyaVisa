package com.konselyavisa.tenancy.web;

import com.konselyavisa.tenancy.TenantContext;
import com.konselyavisa.tenancy.TenantFilters;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class TenantRequestFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(TenantRequestFilter.class);
    static final String ORGANIZATION_CLAIM = "organization_id";
    static final String PLATFORM_ADMIN_ROLE = "ROLE_PLATFORM_ADMIN";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null && authentication.getPrincipal() instanceof Jwt jwt) {
                TenantContext.setPlatformAdmin(hasPlatformAdminRole(authentication));
                try {
                    TenantContext.setOrganizationId(
                            TenantFilters.parseOrganizationId(jwt.getClaimAsString(ORGANIZATION_CLAIM)));
                } catch (IllegalArgumentException ex) {
                    log.warn("Ignoring invalid organization_id JWT claim");
                    TenantContext.setOrganizationId(null);
                }
            }
            filterChain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }

    private static boolean hasPlatformAdminRole(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(PLATFORM_ADMIN_ROLE::equals);
    }
}
