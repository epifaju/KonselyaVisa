package com.konselyavisa.tenancy.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.konselyavisa.tenancy.TenantContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

class TenantRequestFilterTest {

    private final TenantRequestFilter filter = new TenantRequestFilter();
    private HttpServletRequest request;
    private HttpServletResponse response;

    @BeforeEach
    void setUp() {
        request = mock(HttpServletRequest.class);
        response = mock(HttpServletResponse.class);
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    void doFilterInternalSetsOrganizationAndPlatformAdminFromJwt() throws Exception {
        UUID orgId = UUID.randomUUID();
        authenticate(orgId.toString(), "ROLE_PLATFORM_ADMIN");
        AtomicReference<UUID> seenOrg = new AtomicReference<>();
        AtomicReference<Boolean> seenAdmin = new AtomicReference<>();
        FilterChain chain = (req, res) -> {
            seenOrg.set(TenantContext.getOrganizationId());
            seenAdmin.set(TenantContext.isPlatformAdmin());
        };

        filter.doFilterInternal(request, response, chain);

        assertThat(seenOrg.get()).isEqualTo(orgId);
        assertThat(seenAdmin.get()).isTrue();
        assertThat(TenantContext.getOrganizationId()).isNull();
        assertThat(TenantContext.isPlatformAdmin()).isFalse();
    }

    @Test
    void doFilterInternalClearsContextAfterChainEvenOnException() throws Exception {
        authenticate(UUID.randomUUID().toString(), "ROLE_CITIZEN");
        FilterChain chain = (req, res) -> {
            throw new IllegalStateException("boom");
        };

        assertThatThrownBy(() -> filter.doFilterInternal(request, response, chain))
                .isInstanceOf(IllegalStateException.class);
        assertThat(TenantContext.getOrganizationId()).isNull();
    }

    @Test
    void doFilterInternalIgnoresInvalidOrganizationClaim() throws Exception {
        authenticate("not-a-uuid", "ROLE_AGENT");
        AtomicReference<UUID> seenOrg = new AtomicReference<>(UUID.randomUUID());
        FilterChain chain = (req, res) -> seenOrg.set(TenantContext.getOrganizationId());

        filter.doFilterInternal(request, response, chain);

        assertThat(seenOrg.get()).isNull();
    }

    @Test
    void doFilterInternalSkipsContextWhenPrincipalNotJwt() throws Exception {
        SecurityContextHolder.getContext()
                .setAuthentication(new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                        "user", "pwd", List.of()));
        FilterChain chain = mock(FilterChain.class);
        doAnswer(inv -> {
                    assertThat(TenantContext.getOrganizationId()).isNull();
                    return null;
                })
                .when(chain)
                .doFilter(request, response);

        filter.doFilterInternal(request, response, chain);

        verify(chain).doFilter(request, response);
    }

    private static void authenticate(String organizationClaim, String... roles) {
        Jwt jwt = Jwt.withTokenValue("test")
                .header("alg", "none")
                .subject("sub-1")
                .claim(TenantRequestFilter.ORGANIZATION_CLAIM, organizationClaim)
                .build();
        SecurityContextHolder.getContext()
                .setAuthentication(new JwtAuthenticationToken(
                        jwt, java.util.Arrays.stream(roles).map(SimpleGrantedAuthority::new).toList()));
    }
}
