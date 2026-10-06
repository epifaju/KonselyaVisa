package com.konselyavisa.privacy;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Scheduled;

@Configuration
public class RetentionPurgeConfig {

    @Configuration
    @ConditionalOnProperty(name = "konselyavisa.privacy.purge-enabled", havingValue = "true", matchIfMissing = true)
    static class Scheduler {

        private final RetentionPurgeService retentionPurgeService;

        Scheduler(RetentionPurgeService retentionPurgeService) {
            this.retentionPurgeService = retentionPurgeService;
        }

        @Scheduled(fixedDelayString = "${konselyavisa.privacy.purge-poll-ms:60000}")
        void poll() {
            retentionPurgeService.purgeDue();
        }
    }
}
