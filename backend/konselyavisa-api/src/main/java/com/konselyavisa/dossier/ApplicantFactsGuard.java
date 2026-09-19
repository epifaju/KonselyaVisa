package com.konselyavisa.dossier;

import com.konselyavisa.common.exception.BusinessException;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class ApplicantFactsGuard {

    private static final Set<String> FORBIDDEN_FRAGMENTS =
            Set.of("passportnumber", "passeport", "ssn", "iban", "nationalid");

    private ApplicantFactsGuard() {}

    public static Map<String, Object> sanitize(Map<String, Object> facts) {
        if (facts == null || facts.isEmpty()) {
            return Map.of();
        }
        for (String key : facts.keySet()) {
            String normalized = key.toLowerCase(Locale.ROOT).replace("-", "").replace("_", "");
            for (String fragment : FORBIDDEN_FRAGMENTS) {
                if (normalized.contains(fragment)) {
                    throw BusinessException.badRequest("error.case.sensitive_fact_forbidden");
                }
            }
        }
        return facts;
    }
}
