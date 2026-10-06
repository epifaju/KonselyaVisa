package com.konselyavisa.document.crypto;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(ExtractedFieldsCryptoProperties.class)
public class ExtractedFieldsCryptoConfig {

    public ExtractedFieldsCryptoConfig(ExtractedFieldsCryptoProperties properties) {
        String key = properties.getExtractedFieldsKey();
        if (key == null || key.isBlank()) {
            throw new IllegalStateException("konselyavisa.crypto.extracted-fields-key is required");
        }
        ExtractedFieldsKeyHolder.set(key);
    }
}
