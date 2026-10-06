package com.konselyavisa.privacy;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "konselyavisa.privacy")
public class KonselyaPrivacyProperties {

    private Duration anonymizeAfter = Duration.ofDays(1825);
    private boolean purgeEnabled = true;
    private long purgePollMs = 60_000L;
    private int purgeBatchSize = 20;

    public Duration getAnonymizeAfter() {
        return anonymizeAfter;
    }

    public void setAnonymizeAfter(Duration anonymizeAfter) {
        this.anonymizeAfter = anonymizeAfter;
    }

    public boolean isPurgeEnabled() {
        return purgeEnabled;
    }

    public void setPurgeEnabled(boolean purgeEnabled) {
        this.purgeEnabled = purgeEnabled;
    }

    public long getPurgePollMs() {
        return purgePollMs;
    }

    public void setPurgePollMs(long purgePollMs) {
        this.purgePollMs = purgePollMs;
    }

    public int getPurgeBatchSize() {
        return purgeBatchSize;
    }

    public void setPurgeBatchSize(int purgeBatchSize) {
        this.purgeBatchSize = purgeBatchSize;
    }
}
