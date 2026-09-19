package com.konselyavisa.common.i18n;

import com.konselyavisa.common.exception.BusinessException;
import java.util.Map;

public final class LocalizedText {

    public static final String DEFAULT_LOCALE = "fr";

    private LocalizedText() {}

    public static void requireDefaultLocale(Map<String, String> values, String messageKey) {
        if (values == null || values.isEmpty()) {
            throw BusinessException.badRequest(messageKey);
        }
        String defaultValue = values.get(DEFAULT_LOCALE);
        if (defaultValue == null || defaultValue.isBlank()) {
            throw BusinessException.badRequest(messageKey);
        }
    }
}
