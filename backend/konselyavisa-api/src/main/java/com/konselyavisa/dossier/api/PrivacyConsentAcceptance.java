package com.konselyavisa.dossier.api;

import com.konselyavisa.privacy.PrivacyNoticeCatalog;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Size;

public record PrivacyConsentAcceptance(
        @AssertTrue boolean accepted, @Size(max = 8) String locale, @Size(max = 20) String noticeVersion) {

    public static PrivacyConsentAcceptance currentAccepted(String locale) {
        return new PrivacyConsentAcceptance(true, locale, PrivacyNoticeCatalog.CURRENT_VERSION);
    }
}
