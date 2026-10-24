package com.konselyavisa.dossier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.konselyavisa.common.exception.BusinessException;
import com.konselyavisa.dossier.api.CompanyCaseSummaryResponse;
import com.konselyavisa.tenancy.TenantContext;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

@ExtendWith(MockitoExtension.class)
class CompanyCaseServiceTest {

    @Mock
    private CaseFileRepository caseFileRepository;

    private CompanyCaseService service;

    @BeforeEach
    void setUp() {
        service = new CompanyCaseService(caseFileRepository);
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    void summaryForbiddenForNonCompanyWorkspace() {
        authenticateAs("agent-1", "agent.dev", "ROLE_AGENT");

        assertThatThrownBy(() -> service.summary())
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getMessageKey())
                .isEqualTo("error.access.denied");
    }

    @Test
    void summaryForbiddenWhenCollaboratorUsernameMissing() {
        org.springframework.security.core.Authentication authentication =
                org.mockito.Mockito.mock(org.springframework.security.core.Authentication.class);
        org.mockito.Mockito.when(authentication.getAuthorities())
                .thenReturn((java.util.Collection) List.of(new SimpleGrantedAuthority("ROLE_COMPANY_USER")));
        org.mockito.Mockito.when(authentication.getName()).thenReturn("anonymousUser");
        SecurityContextHolder.getContext().setAuthentication(authentication);

        assertThatThrownBy(() -> service.summary())
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getMessageKey())
                .isEqualTo("error.access.denied");
        verify(caseFileRepository, never()).countGroupedByCreatedBy(anyString());
    }

    @Test
    void summaryCollaboratorScopesByUsername() {
        authenticateAs("company-1", "company.dev", "ROLE_COMPANY_USER");
        when(caseFileRepository.countGroupedByCreatedBy("company.dev"))
                .thenReturn(List.of(count(CaseStatus.IN_PROGRESS, 2), count(null, 9)));

        CompanyCaseSummaryResponse summary = service.summary();

        assertThat(summary.total()).isEqualTo(2);
        assertThat(summary.byStatus().get(CaseStatus.IN_PROGRESS)).isEqualTo(2L);
        assertThat(summary.byStatus().get(CaseStatus.CREATED)).isZero();
        assertThat(summary.byStatus()).containsOnlyKeys(CaseStatus.values());
        verify(caseFileRepository, never()).countGroupedByCreatedByRoleIn(anyList());
    }

    @Test
    void summaryOrgAdminAggregatesCompanyRoles() {
        authenticateAs("admin-1", "org.admin", "ROLE_COMPANY_USER", "ROLE_BUSINESS_ADMIN");
        when(caseFileRepository.countGroupedByCreatedByRoleIn(List.of("COMPANY_USER", "COMPANY_ADMIN")))
                .thenReturn(List.of(count(CaseStatus.CREATED, 1), count(CaseStatus.COMPLETED, 3)));

        CompanyCaseSummaryResponse summary = service.summary();

        assertThat(summary.total()).isEqualTo(4);
        assertThat(summary.byStatus().get(CaseStatus.CREATED)).isEqualTo(1L);
        assertThat(summary.byStatus().get(CaseStatus.COMPLETED)).isEqualTo(3L);
        verify(caseFileRepository).countGroupedByCreatedByRoleIn(List.of("COMPANY_USER", "COMPANY_ADMIN"));
        verify(caseFileRepository, never()).countGroupedByCreatedBy(anyString());
    }

    private static CaseStatusCount count(CaseStatus status, long total) {
        return new CaseStatusCount() {
            @Override
            public CaseStatus getStatus() {
                return status;
            }

            @Override
            public long getTotal() {
                return total;
            }
        };
    }

    private static void authenticateAs(String subject, String username, String... roles) {
        Jwt jwt = Jwt.withTokenValue("test")
                .header("alg", "none")
                .subject(subject)
                .claim("preferred_username", username)
                .build();
        SecurityContextHolder.getContext()
                .setAuthentication(new JwtAuthenticationToken(
                        jwt, java.util.Arrays.stream(roles).map(SimpleGrantedAuthority::new).toList()));
    }
}
