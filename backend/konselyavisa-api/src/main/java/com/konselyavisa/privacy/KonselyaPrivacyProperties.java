package com.konselyavisa.privacy;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "konselyavisa.privacy")
public class KonselyaPrivacyProperties {

    private Duration anonymizeAfter = Duration.ofDays(1825);

    public Duration getAnonymizeAfter() {
        return anonymizeAfter;
    }

    public void setAnonymizeAfter(Duration anonymizeAfter) {
        this.anonymizeAfter = anonymizeAfter;
    }
}
