package com.konselyavisa.redis;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

public class RedisSlotHoldStore implements SlotHoldStore {

    private static final DefaultRedisScript<Long> ACQUIRE = new DefaultRedisScript<>(
            """
            redis.call('ZREMRANGEBYSCORE', KEYS[1], '-inf', ARGV[3])
            if redis.call('ZSCORE', KEYS[1], ARGV[4]) then
              redis.call('ZADD', KEYS[1], ARGV[5], ARGV[4])
              redis.call('EXPIRE', KEYS[1], ARGV[2])
              return 1
            end
            if redis.call('ZCARD', KEYS[1]) >= tonumber(ARGV[1]) then
              return 0
            end
            redis.call('ZADD', KEYS[1], ARGV[5], ARGV[4])
            redis.call('EXPIRE', KEYS[1], ARGV[2])
            return 1
            """,
            Long.class);

    private static final DefaultRedisScript<Long> RELEASE = new DefaultRedisScript<>(
            """
            redis.call('ZREM', KEYS[1], ARGV[1])
            redis.call('ZREMRANGEBYSCORE', KEYS[1], '-inf', ARGV[2])
            return redis.call('ZCARD', KEYS[1])
            """,
            Long.class);

    private static final DefaultRedisScript<Long> COUNT = new DefaultRedisScript<>(
            """
            redis.call('ZREMRANGEBYSCORE', KEYS[1], '-inf', ARGV[1])
            return redis.call('ZCARD', KEYS[1])
            """,
            Long.class);

    private final StringRedisTemplate redis;

    public RedisSlotHoldStore(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public boolean tryAcquire(UUID organizationId, UUID slotId, UUID caseId, int maxHolds, Duration ttl) {
        if (maxHolds < 1) {
            return false;
        }
        long ttlSeconds = Math.max(1, ttl.toSeconds());
        long now = Instant.now().getEpochSecond();
        Long result = redis.execute(
                ACQUIRE,
                List.of(countKey(organizationId, slotId)),
                String.valueOf(maxHolds),
                String.valueOf(ttlSeconds + 5),
                String.valueOf(now),
                caseId.toString(),
                String.valueOf(now + ttlSeconds));
        return result != null && result == 1L;
    }

    @Override
    public void release(UUID organizationId, UUID slotId, UUID caseId) {
        redis.execute(
                RELEASE,
                List.of(countKey(organizationId, slotId)),
                caseId.toString(),
                String.valueOf(Instant.now().getEpochSecond()));
    }

    @Override
    public int activeCount(UUID organizationId, UUID slotId) {
        Long count = redis.execute(
                COUNT, List.of(countKey(organizationId, slotId)), String.valueOf(Instant.now().getEpochSecond()));
        return count == null ? 0 : Math.max(0, count.intValue());
    }

    private static String countKey(UUID organizationId, UUID slotId) {
        return "kv:ahc:" + organizationId + ":" + slotId;
    }
}
