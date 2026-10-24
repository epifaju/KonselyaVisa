package com.konselyavisa.dossier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

import com.konselyavisa.catalog.domain.ProcedureVersion;
import com.konselyavisa.common.exception.BusinessException;
import com.konselyavisa.dossier.api.SupervisorSummaryResponse;
import com.konselyavisa.tenancy.TenantContext;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
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
class SupervisorSummaryServiceTest {

    @Mock
    private CaseFileRepository caseFileRepository;

    private SupervisorSummaryService service;

    @BeforeEach
    void setUp() {
        service = new SupervisorSummaryService(caseFileRepository);
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    void summarizeForbiddenForAgent() {
        authenticateAs("agent-1", "agent.dev", "ROLE_AGENT");

        assertThatThrownBy(() -> service.summarize())
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getMessageKey())
                .isEqualTo("error.access.denied");
    }

    @Test
    void summarizeEmptyOpenCases() {
        authenticateAs("sup-1", "sup.dev", "ROLE_SUPERVISOR");
        when(caseFileRepository.findByStatusIn(anyList())).thenReturn(List.of());

        assertThat(service.summarize()).isEqualTo(new SupervisorSummaryResponse(0, 0, 0));
    }

    @Test
    void summarizeCountsWatchAndOverdueWithFallbackSla() {
        authenticateAs("admin-1", "admin.dev", "ROLE_BUSINESS_ADMIN");
        CaseFile overdue = openCase(Instant.now().minus(130, ChronoUnit.HOURS), null);
        CaseFile watch = openCase(Instant.now().minus(50, ChronoUnit.HOURS), null);
        CaseFile ok = openCase(Instant.now().minus(1, ChronoUnit.HOURS), null);
        when(caseFileRepository.findByStatusIn(anyList())).thenReturn(List.of(overdue, watch, ok));

        SupervisorSummaryResponse summary = service.summarize();

        assertThat(summary.openCount()).isEqualTo(3);
        assertThat(summary.watchCount()).isEqualTo(1);
        assertThat(summary.overdueCount()).isEqualTo(1);
    }

    @Test
    void summarizeUsesProcedureVersionSlaHours() {
        authenticateAs("plat-1", "plat.dev", "ROLE_PLATFORM_ADMIN");
        ProcedureVersion version = new ProcedureVersion();
        version.setEstimatedInstructionDays(5);
        CaseFile watch = openCase(Instant.now().minus(70, ChronoUnit.HOURS), version);
        when(caseFileRepository.findByStatusIn(anyList())).thenReturn(List.of(watch));

        SupervisorSummaryResponse summary = service.summarize();

        assertThat(summary.openCount()).isEqualTo(1);
        assertThat(summary.watchCount()).isEqualTo(1);
        assertThat(summary.overdueCount()).isZero();
    }

    private static CaseFile openCase(Instant createdAt, ProcedureVersion version) {
        CaseFile caseFile = new CaseFile();
        caseFile.setStatus(CaseStatus.IN_PROGRESS);
        caseFile.setCreatedAt(createdAt);
        caseFile.setProcedureVersion(version);
        return caseFile;
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
