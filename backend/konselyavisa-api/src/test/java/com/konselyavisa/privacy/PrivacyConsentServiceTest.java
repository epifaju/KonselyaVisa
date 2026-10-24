package com.konselyavisa.privacy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.konselyavisa.applicant.Applicant;
import com.konselyavisa.common.exception.BusinessException;
import com.konselyavisa.dossier.CaseFile;
import com.konselyavisa.dossier.api.PrivacyConsentAcceptance;
import com.konselyavisa.privacy.domain.PrivacyConsent;
import com.konselyavisa.privacy.domain.PrivacyConsentPurpose;
import com.konselyavisa.privacy.persistence.PrivacyConsentRepository;
import com.konselyavisa.tenancy.TenantContext;
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
class PrivacyConsentServiceTest {

    @Mock
    private PrivacyConsentRepository privacyConsentRepository;

    private PrivacyConsentService service;

    @BeforeEach
    void setUp() {
        service = new PrivacyConsentService(privacyConsentRepository);
        TenantContext.clear();
        SecurityContextHolder.clearContext();
        lenient()
                .when(privacyConsentRepository.saveAndFlush(any(PrivacyConsent.class)))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    void recordCaseDepositRejectsWhenNotAccepted() {
        assertThatThrownBy(() -> service.recordCaseDeposit(
                        new CaseFile(), new Applicant(), new PrivacyConsentAcceptance(false, "fr", "1")))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getMessageKey())
                .isEqualTo("error.privacy.consent_required");
        verify(privacyConsentRepository, never()).saveAndFlush(any());
    }

    @Test
    void recordCaseDepositRejectsStaleNoticeVersion() {
        assertThatThrownBy(() -> service.recordCaseDeposit(
                        new CaseFile(), new Applicant(), new PrivacyConsentAcceptance(true, "fr", "99")))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getMessageKey())
                .isEqualTo("error.privacy.notice_stale");
    }

    @Test
    void recordCaseDepositDefaultsLocaleAndPersists() {
        authenticateAs("citizen-1");
        CaseFile caseFile = new CaseFile();
        Applicant applicant = new Applicant();

        PrivacyConsent consent = service.recordCaseDeposit(
                caseFile, applicant, new PrivacyConsentAcceptance(true, "  ", PrivacyNoticeCatalog.CURRENT_VERSION));

        ArgumentCaptor<PrivacyConsent> captor = ArgumentCaptor.forClass(PrivacyConsent.class);
        verify(privacyConsentRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getPurpose()).isEqualTo(PrivacyConsentPurpose.CASE_DEPOSIT);
        assertThat(captor.getValue().getLocale()).isEqualTo("fr");
        assertThat(captor.getValue().getKeycloakSubject()).isEqualTo("citizen-1");
        assertThat(captor.getValue().getCaseFile()).isSameAs(caseFile);
        assertThat(consent.getAcceptedAt()).isNotNull();
    }

    @Test
    void recordAccountCreationRejectsBlankSubject() {
        assertThatThrownBy(() -> service.recordAccountCreation(
                        "  ", PrivacyConsentAcceptance.currentAccepted("fr")))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getMessageKey())
                .isEqualTo("error.validation");
    }

    @Test
    void recordAccountCreationPersistsWithProvidedSubject() {
        PrivacyConsent consent = service.recordAccountCreation(
                "kc-subject", new PrivacyConsentAcceptance(true, "pt-BR", PrivacyNoticeCatalog.CURRENT_VERSION));

        assertThat(consent.getPurpose()).isEqualTo(PrivacyConsentPurpose.ACCOUNT_CREATION);
        assertThat(consent.getKeycloakSubject()).isEqualTo("kc-subject");
        assertThat(consent.getLocale()).isEqualTo("pt");
        assertThat(consent.getTextAccepted()).isEqualTo(PrivacyNoticeCatalog.text("pt"));
    }

    private static void authenticateAs(String subject) {
        Jwt jwt = Jwt.withTokenValue("test").header("alg", "none").subject(subject).build();
        SecurityContextHolder.getContext()
                .setAuthentication(new JwtAuthenticationToken(
                        jwt, java.util.List.of(new SimpleGrantedAuthority("ROLE_CITIZEN"))));
    }
}
