package com.konselyavisa.document.crypto;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "konselyavisa.crypto")
public class ExtractedFieldsCryptoProperties {

    /**
     * Passphrase for pgcrypto {@code pgp_sym_encrypt} / {@code pgp_sym_decrypt}
     * of {@code documents.extracted_fields_cipher}.
     */
    private String extractedFieldsKey = "";

    public String getExtractedFieldsKey() {
        return extractedFieldsKey;
    }

    public void setExtractedFieldsKey(String extractedFieldsKey) {
        this.extractedFieldsKey = extractedFieldsKey;
    }
}
