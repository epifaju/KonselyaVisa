package com.konselyavisa.document;

public final class ReviewMessageKeys {

    public static final String DEFAULT_CORRECTION = "error.document.correction_requested";

    private ReviewMessageKeys() {}

    public static String fromReason(String reason) {
        if (reason == null || reason.isBlank()) {
            return DEFAULT_CORRECTION;
        }
        String key = reason.lines().findFirst().orElse("").trim();
        if (key.startsWith("error.") || key.startsWith("document.correction.")) {
            return key;
        }
        return DEFAULT_CORRECTION;
    }
}
