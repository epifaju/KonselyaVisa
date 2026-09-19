package com.konselyavisa.privacy;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public final class PrivacyNoticeCatalog {

    public static final String CURRENT_VERSION = "1";

    private static final Map<String, String> TEXTS = Map.of(
            "fr",
            "Je consens au traitement de mes données d’identité et de voyage par l’organisation "
                    + "consulaire pour instruire ce dossier, conformément au registre des traitements KonselyaVisa. "
                    + "Je peux demander un export ou une anonymisation différée via Mon compte.",
            "pt",
            "Consento no tratamento dos meus dados de identidade e de viagem pela organização "
                    + "consular para instruir este processo, nos termos do registo de tratamentos KonselyaVisa. "
                    + "Posso pedir uma exportação ou uma anonimização diferida na minha conta.",
            "en",
            "I consent to the processing of my identity and travel data by the consular organization "
                    + "to handle this case, as described in the KonselyaVisa record of processing activities. "
                    + "I may request an export or deferred anonymisation from my account.");

    private PrivacyNoticeCatalog() {}

    public static String text(String locale) {
        String lang = locale == null || locale.isBlank() ? "fr" : locale.toLowerCase(Locale.ROOT);
        if (lang.length() > 2) {
            lang = lang.substring(0, 2);
        }
        return TEXTS.getOrDefault(lang, TEXTS.get("fr"));
    }

    public static Map<String, String> texts() {
        return new LinkedHashMap<>(TEXTS);
    }

    public static void requireCurrentVersion(String noticeVersion) {
        if (noticeVersion != null && !noticeVersion.isBlank() && !CURRENT_VERSION.equals(noticeVersion)) {
            throw com.konselyavisa.common.exception.BusinessException.badRequest("error.privacy.notice_stale");
        }
    }
}
