package com.konselyavisa.guest;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;

public class RedisGuestEligibilityTicketStore implements GuestEligibilityTicketStore {

    private static final String KEY_PREFIX = "guest:eligibility:";

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    public RedisGuestEligibilityTicketStore(StringRedisTemplate redis, ObjectMapper objectMapper) {
        this.redis = redis;
        this.objectMapper = objectMapper;
    }

    @Override
    public void save(GuestEligibilityTicket ticket, Duration ttl) {
        try {
            redis.opsForValue().set(KEY_PREFIX + ticket.id(), objectMapper.writeValueAsString(ticket), ttl);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Unable to serialize guest ticket", ex);
        }
    }

    @Override
    public Optional<GuestEligibilityTicket> consume(UUID ticketId) {
        String key = KEY_PREFIX + ticketId;
        String json = redis.opsForValue().get(key);
        if (json == null || json.isBlank()) {
            return Optional.empty();
        }
        redis.delete(key);
        try {
            return Optional.of(objectMapper.readValue(json, GuestEligibilityTicket.class));
        } catch (JsonProcessingException ex) {
            return Optional.empty();
        }
    }
}
