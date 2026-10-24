package com.konselyavisa.dossier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.konselyavisa.common.api.PageResponse;
import com.konselyavisa.common.exception.BusinessException;
import com.konselyavisa.dossier.api.CaseResponse;
import com.konselyavisa.tenancy.TenantContext;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

@ExtendWith(MockitoExtension.class)
class AgentCaseQueryServiceTest {

    @Mock
    private CaseFileRepository caseFileRepository;

    @Mock
    private CaseResponseAssembler caseResponseAssembler;

    private AgentCaseQueryService service;

    @BeforeEach
    void setUp() {
        service = new AgentCaseQueryService(caseFileRepository, caseResponseAssembler);
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    void listForbiddenForCitizenSelfScoped() {
        authenticateAs("citizen-1", "citizen.dev", "ROLE_CITIZEN");

        assertThatThrownBy(() -> service.list(null, PageRequest.of(0, 10)))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getMessageKey())
                .isEqualTo("error.access.denied");
    }

    @Test
    void listWithoutStatusUsesFindAll() {
        authenticateAs("agent-1", "agent.dev", "ROLE_AGENT");
        Pageable pageable = PageRequest.of(0, 10);
        CaseFile caseFile = new CaseFile();
        CaseResponse mapped = org.mockito.Mockito.mock(CaseResponse.class);
        when(caseFileRepository.findAll(pageable)).thenReturn(new PageImpl<>(List.of(caseFile), pageable, 1));
        when(caseResponseAssembler.toResponses(List.of(caseFile))).thenReturn(List.of(mapped));

        PageResponse<CaseResponse> page = service.list(null, pageable);

        assertThat(page.content()).containsExactly(mapped);
        assertThat(page.page()).isZero();
        assertThat(page.size()).isEqualTo(10);
        assertThat(page.totalElements()).isEqualTo(1);
        verify(caseFileRepository).findAll(pageable);
    }

    @Test
    void listWithStatusFiltersRepository() {
        authenticateAs("agent-1", "agent.dev", "ROLE_AGENT");
        Pageable pageable = PageRequest.of(1, 5);
        when(caseFileRepository.findAllByStatus(CaseStatus.IN_PROGRESS, pageable))
                .thenReturn(new PageImpl<>(List.of(), pageable, 0));
        when(caseResponseAssembler.toResponses(List.of())).thenReturn(List.of());

        PageResponse<CaseResponse> page = service.list(CaseStatus.IN_PROGRESS, pageable);

        assertThat(page.content()).isEmpty();
        assertThat(page.page()).isEqualTo(1);
        assertThat(page.size()).isEqualTo(5);
        verify(caseFileRepository).findAllByStatus(CaseStatus.IN_PROGRESS, pageable);
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
