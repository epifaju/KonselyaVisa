package com.konselyavisa.identity.api;

import com.konselyavisa.common.api.ApiResponse;
import com.konselyavisa.tenancy.TenantContext;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/me")
public class MeController {

    @GetMapping
    public ApiResponse<CurrentUserResponse> me(JwtAuthenticationToken authentication) {
        String username = authentication.getToken().getClaimAsString("preferred_username");
        if (username == null || username.isBlank()) {
            username = authentication.getName();
        }
        List<String> roles = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .toList();
        UUID organizationId = TenantContext.getOrganizationId();
        return ApiResponse.ok(new CurrentUserResponse(username, roles, organizationId));
    }

    public record CurrentUserResponse(String username, List<String> roles, UUID organizationId) {}
}
