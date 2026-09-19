package com.konselyavisa.redis;

import com.konselyavisa.common.exception.BusinessException;
import java.time.Instant;
import java.util.concurrent.TimeUnit;
import org.springframework.data.redis.core.StringRedisTemplate;

public class RedisRateLimiter {

    private final StringRedisTemplate redis;
    private final KonselyaRedisProperties properties;

    public RedisRateLimiter(StringRedisTemplate redis, KonselyaRedisProperties properties) {
        this.redis = redis;
        this.properties = properties;
    }

    public void check(String bucket, boolean write) {
        int limit = write ? properties.getWritePerMinute() : properties.getReadPerMinute();
        if (limit < 1) {
            return;
        }
        long window = Instant.now().getEpochSecond() / 60;
        String key = "kv:rl:" + window + ":" + (write ? "w:" : "r:") + bucket;
        Long count = redis.opsForValue().increment(key);
        if (count != null && count == 1L) {
            redis.expire(key, 70, TimeUnit.SECONDS);
        }
        if (count != null && count > limit) {
            throw BusinessException.tooManyRequests("error.rate_limited");
        }
    }
}
