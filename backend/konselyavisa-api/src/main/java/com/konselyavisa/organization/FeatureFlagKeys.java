package com.konselyavisa.organization;

import java.util.Locale;

public final class FeatureFlagKeys {

    public static final String PROCEDURE_PREFIX = "procedure.";
    public static final String PAYMENT_PROVIDER_PREFIX = "payment.provider.";

    private FeatureFlagKeys() {}

    public static String procedure(String procedureCode) {
        return PROCEDURE_PREFIX + procedureCode;
    }

    public static String paymentProvider(String providerCode) {
        return PAYMENT_PROVIDER_PREFIX + providerCode.toUpperCase(Locale.ROOT);
    }
}
