package com.konselyavisa.redis;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "konselyavisa.redis")
public class KonselyaRedisProperties {

    private boolean enabled = false;
    private Duration slotHoldTtl = Duration.ofMinutes(2);
    private Duration catalogTtl = Duration.ofMinutes(2);
    private int writePerMinute = 60;
    private int readPerMinute = 300;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Duration getSlotHoldTtl() {
        return slotHoldTtl;
    }

    public void setSlotHoldTtl(Duration slotHoldTtl) {
        this.slotHoldTtl = slotHoldTtl;
    }

    public Duration getCatalogTtl() {
        return catalogTtl;
    }

    public void setCatalogTtl(Duration catalogTtl) {
        this.catalogTtl = catalogTtl;
    }

    public int getWritePerMinute() {
        return writePerMinute;
    }

    public void setWritePerMinute(int writePerMinute) {
        this.writePerMinute = writePerMinute;
    }

    public int getReadPerMinute() {
        return readPerMinute;
    }

    public void setReadPerMinute(int readPerMinute) {
        this.readPerMinute = readPerMinute;
    }
}
