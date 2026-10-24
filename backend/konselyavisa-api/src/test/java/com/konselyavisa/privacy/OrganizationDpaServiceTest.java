package com.konselyavisa.privacy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.konselyavisa.common.exception.BusinessException;
import com.konselyavisa.privacy.api.AcceptOrganizationDpaRequest;
import com.konselyavisa.privacy.api.OrganizationDpaMapper;
import com.konselyavisa.privacy.api.OrganizationDpaResponse;
import com.konselyavisa.privacy.domain.OrganizationDpaAgreement;
import com.konselyavisa.privacy.domain.OrganizationDpaStatus;
import com.konselyavisa.privacy.persistence.OrganizationDpaAgreementRepository;
import com.konselyavisa.tenancy.TenantContext;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

@ExtendWith(MockitoExtension.class)
class OrganizationDpaServiceTest {

    @Mock
    private OrganizationDpaAgreementRepository repository;

    @Mock
    private OrganizationDpaMapper mapper;

    private OrganizationDpaService service;
    private UUID orgId;

    @BeforeEach
    void setUp() {
        orgId = UUID.randomUUID();
        service = new OrganizationDpaService(repository, mapper);
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    void findActiveForCurrentOrgForbiddenWhenTenantMissing() {
        assertThatThrownBy(() -> service.findActiveForCurrentOrg())
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getMessageKey())
                .isEqualTo("error.organization.missing");
    }

    @Test
    void acceptRejectsStaleVersion() {
        TenantContext.setOrganizationId(orgId);

        assertThatThrownBy(() -> service.accept(new AcceptOrganizationDpaRequest(
                        "1999.01", Map.of("fr", "Org"), null)))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getMessageKey())
                .isEqualTo("error.privacy.dpa_version_stale");
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void acceptSupersedesExistingActiveAgreement() {
        TenantContext.setOrganizationId(orgId);
        authenticateAs("admin-1", "org.admin");
        OrganizationDpaAgreement existing = new OrganizationDpaAgreement();
        existing.setStatus(OrganizationDpaStatus.ACTIVE);
        when(repository.findByOrganizationIdAndStatus(orgId, OrganizationDpaStatus.ACTIVE))
                .thenReturn(Optional.of(existing));
        when(repository.saveAndFlush(any(OrganizationDpaAgreement.class))).thenAnswer(inv -> inv.getArgument(0));
        OrganizationDpaResponse mapped = org.mockito.Mockito.mock(OrganizationDpaResponse.class);
        when(mapper.toResponse(any(OrganizationDpaAgreement.class))).thenReturn(mapped);

        assertThat(service.accept(new AcceptOrganizationDpaRequest(
                        OrganizationDpaCatalog.CURRENT_VERSION, Map.of("fr", " Konselya "), "  ")))
                .isSameAs(mapped);
        assertThat(existing.getStatus()).isEqualTo(OrganizationDpaStatus.SUPERSEDED);

        ArgumentCaptor<OrganizationDpaAgreement> captor = ArgumentCaptor.forClass(OrganizationDpaAgreement.class);
        verify(repository, org.mockito.Mockito.atLeastOnce()).saveAndFlush(captor.capture());
        OrganizationDpaAgreement created = captor.getAllValues().get(captor.getAllValues().size() - 1);
        assertThat(created.getStatus()).isEqualTo(OrganizationDpaStatus.ACTIVE);
        assertThat(created.getOrganizationId()).isEqualTo(orgId);
        assertThat(created.getControllerNameI18n()).containsEntry("fr", "Konselya");
        assertThat(created.getDocumentUri()).isNull();
        assertThat(created.getAcceptedByLabel()).isEqualTo("org.admin");
    }

    @Test
    void acceptRejectsControllerNamesWithoutFrench() {
        TenantContext.setOrganizationId(orgId);

        assertThatThrownBy(() -> service.accept(new AcceptOrganizationDpaRequest(
                        OrganizationDpaCatalog.CURRENT_VERSION, Map.of("en", "Org"), null)))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getMessageKey())
                .isEqualTo("error.privacy.dpa_controller_required");
    }

    @Test
    void acceptUsesSubjectWhenUsernameMissing() {
        TenantContext.setOrganizationId(orgId);
        Jwt jwt = Jwt.withTokenValue("test").header("alg", "none").subject("subj-42").build();
        SecurityContextHolder.getContext()
                .setAuthentication(new JwtAuthenticationToken(
                        jwt, java.util.List.of(new SimpleGrantedAuthority("ROLE_BUSINESS_ADMIN"))));
        when(repository.findByOrganizationIdAndStatus(orgId, OrganizationDpaStatus.ACTIVE))
                .thenReturn(Optional.empty());
        when(repository.saveAndFlush(any(OrganizationDpaAgreement.class))).thenAnswer(inv -> inv.getArgument(0));
        when(mapper.toResponse(any(OrganizationDpaAgreement.class)))
                .thenReturn(org.mockito.Mockito.mock(OrganizationDpaResponse.class));

        service.accept(new AcceptOrganizationDpaRequest(
                OrganizationDpaCatalog.CURRENT_VERSION, Map.of("fr", "Org"), "https://dpa.example/doc.pdf"));

        ArgumentCaptor<OrganizationDpaAgreement> captor = ArgumentCaptor.forClass(OrganizationDpaAgreement.class);
        verify(repository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getAcceptedByLabel()).isEqualTo("subj-42");
        assertThat(captor.getValue().getDocumentUri()).isEqualTo("https://dpa.example/doc.pdf");
    }

    private static void authenticateAs(String subject, String username) {
        Jwt jwt = Jwt.withTokenValue("test")
                .header("alg", "none")
                .subject(subject)
                .claim("preferred_username", username)
                .build();
        SecurityContextHolder.getContext()
                .setAuthentication(new JwtAuthenticationToken(
                        jwt, java.util.List.of(new SimpleGrantedAuthority("ROLE_BUSINESS_ADMIN"))));
    }
}
