package com.konselyavisa.outbox;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

@Configuration
@EnableConfigurationProperties(OutboxProperties.class)
@EnableScheduling
public class OutboxPublisherConfig {

    @Configuration
    @ConditionalOnProperty(name = "konselyavisa.outbox.enabled", havingValue = "true", matchIfMissing = true)
    static class Scheduler {

        private final OutboxPublisher publisher;

        Scheduler(OutboxPublisher publisher) {
            this.publisher = publisher;
        }

        @Scheduled(fixedDelayString = "${konselyavisa.outbox.poll-ms:5000}")
        void poll() {
            publisher.publishDue();
        }
    }
}
