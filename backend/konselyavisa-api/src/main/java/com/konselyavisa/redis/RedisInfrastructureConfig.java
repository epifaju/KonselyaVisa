package com.konselyavisa.redis;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.web.servlet.HandlerExceptionResolver;

@Configuration
@EnableConfigurationProperties(KonselyaRedisProperties.class)
public class RedisInfrastructureConfig {

    @Bean
    @ConditionalOnProperty(prefix = "konselyavisa.redis", name = "enabled", havingValue = "false", matchIfMissing = true)
    SlotHoldStore noOpSlotHoldStore() {
        return new NoOpSlotHoldStore();
    }

    @Configuration
    @ConditionalOnProperty(prefix = "konselyavisa.redis", name = "enabled", havingValue = "true")
    @EnableCaching
    static class EnabledRedisConfig {

        @Bean(destroyMethod = "destroy")
        LettuceConnectionFactory redisConnectionFactory(
                @Value("${spring.data.redis.host:localhost}") String host,
                @Value("${spring.data.redis.port:6379}") int port) {
            LettuceConnectionFactory factory =
                    new LettuceConnectionFactory(new RedisStandaloneConfiguration(host, port));
            factory.afterPropertiesSet();
            return factory;
        }

        @Bean
        StringRedisTemplate stringRedisTemplate(LettuceConnectionFactory redisConnectionFactory) {
            return new StringRedisTemplate(redisConnectionFactory);
        }

        @Bean
        SlotHoldStore slotHoldStore(StringRedisTemplate stringRedisTemplate) {
            return new RedisSlotHoldStore(stringRedisTemplate);
        }

        @Bean
        RedisCacheManager cacheManager(
                LettuceConnectionFactory redisConnectionFactory,
                ObjectMapper objectMapper,
                KonselyaRedisProperties properties) {
            Duration ttl = properties.getCatalogTtl() == null ? Duration.ofMinutes(2) : properties.getCatalogTtl();
            RedisCacheConfiguration configuration = RedisCacheConfiguration.defaultCacheConfig()
                    .entryTtl(ttl)
                    .serializeKeysWith(
                            RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
                    .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(
                            new GenericJackson2JsonRedisSerializer(objectMapper)));
            return RedisCacheManager.builder(redisConnectionFactory).cacheDefaults(configuration).build();
        }

        @Bean
        RedisRateLimiter redisRateLimiter(StringRedisTemplate stringRedisTemplate, KonselyaRedisProperties properties) {
            return new RedisRateLimiter(stringRedisTemplate, properties);
        }

        @Bean
        RedisRateLimitFilter redisRateLimitFilter(
                RedisRateLimiter redisRateLimiter,
                @Qualifier("handlerExceptionResolver") HandlerExceptionResolver exceptionResolver) {
            return new RedisRateLimitFilter(redisRateLimiter, exceptionResolver);
        }

        @Bean
        FilterRegistrationBean<RedisRateLimitFilter> redisRateLimitFilterRegistration(
                RedisRateLimitFilter redisRateLimitFilter) {
            FilterRegistrationBean<RedisRateLimitFilter> registration =
                    new FilterRegistrationBean<>(redisRateLimitFilter);
            registration.setEnabled(false);
            return registration;
        }
    }
}
