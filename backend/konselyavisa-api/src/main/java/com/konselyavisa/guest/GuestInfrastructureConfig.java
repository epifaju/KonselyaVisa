package com.konselyavisa.guest;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

@Configuration
@EnableConfigurationProperties(GuestProperties.class)
public class GuestInfrastructureConfig {

    @Bean
    @ConditionalOnProperty(prefix = "konselyavisa.redis", name = "enabled", havingValue = "false", matchIfMissing = true)
    GuestEligibilityTicketStore inMemoryGuestEligibilityTicketStore() {
        return new InMemoryGuestEligibilityTicketStore();
    }

    @Bean
    @ConditionalOnProperty(prefix = "konselyavisa.redis", name = "enabled", havingValue = "true")
    GuestEligibilityTicketStore redisGuestEligibilityTicketStore(
            StringRedisTemplate stringRedisTemplate, ObjectMapper objectMapper) {
        return new RedisGuestEligibilityTicketStore(stringRedisTemplate, objectMapper);
    }
}
