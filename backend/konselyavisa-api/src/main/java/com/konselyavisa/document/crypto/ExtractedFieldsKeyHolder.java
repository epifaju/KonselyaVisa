package com.konselyavisa.document.crypto;

public final class ExtractedFieldsKeyHolder {

    private static volatile String key = "";

    private ExtractedFieldsKeyHolder() {}

    public static void set(String value) {
        key = value == null ? "" : value;
    }

    public static String get() {
        return key;
    }
}
