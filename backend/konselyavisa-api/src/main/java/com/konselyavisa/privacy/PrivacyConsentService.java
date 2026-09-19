package com.konselyavisa.privacy;

import com.konselyavisa.applicant.Applicant;
import com.konselyavisa.common.exception.BusinessException;
import com.konselyavisa.dossier.CaseFile;
import com.konselyavisa.dossier.api.PrivacyConsentAcceptance;
import com.konselyavisa.identity.CurrentUser;
import com.konselyavisa.privacy.domain.PrivacyConsent;
import com.konselyavisa.privacy.domain.PrivacyConsentPurpose;
import com.konselyavisa.privacy.persistence.PrivacyConsentRepository;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PrivacyConsentService {

    private final PrivacyConsentRepository privacyConsentRepository;

    public PrivacyConsentService(PrivacyConsentRepository privacyConsentRepository) {
        this.privacyConsentRepository = privacyConsentRepository;
    }

    @Transactional
    public PrivacyConsent recordCaseDeposit(CaseFile caseFile, Applicant applicant, PrivacyConsentAcceptance acceptance) {
        if (acceptance == null || !acceptance.accepted()) {
            throw BusinessException.badRequest("error.privacy.consent_required");
        }
        PrivacyNoticeCatalog.requireCurrentVersion(acceptance.noticeVersion());
        String locale = acceptance.locale() == null || acceptance.locale().isBlank() ? "fr" : acceptance.locale();
        PrivacyConsent consent = new PrivacyConsent();
        consent.setCaseFile(caseFile);
        consent.setApplicant(applicant);
        consent.setKeycloakSubject(CurrentUser.subject());
        consent.setPurpose(PrivacyConsentPurpose.CASE_DEPOSIT);
        consent.setNoticeVersion(PrivacyNoticeCatalog.CURRENT_VERSION);
        consent.setLocale(locale.length() > 2 ? locale.substring(0, 2) : locale);
        consent.setTextAccepted(PrivacyNoticeCatalog.text(locale));
        consent.setAcceptedAt(Instant.now());
        return privacyConsentRepository.saveAndFlush(consent);
    }
}
