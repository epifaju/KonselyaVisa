package com.konselyavisa.guest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@ExtendWith(MockitoExtension.class)
class RedisGuestEligibilityTicketStoreTest {

    @Mock
    private StringRedisTemplate redis;

    @Mock
    private ValueOperations<String, String> valueOps;

    private ObjectMapper objectMapper;
    private RedisGuestEligibilityTicketStore store;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().findAndRegisterModules();
        lenient().when(redis.opsForValue()).thenReturn(valueOps);
        store = new RedisGuestEligibilityTicketStore(redis, objectMapper);
    }

    @Test
    void saveSerializesTicketAndSetsTtl() throws Exception {
        GuestEligibilityTicket ticket = ticket();
        Duration ttl = Duration.ofMinutes(15);

        store.save(ticket, ttl);

        ArgumentCaptor<String> jsonCaptor = ArgumentCaptor.forClass(String.class);
        verify(valueOps).set(eq("guest:eligibility:" + ticket.id()), jsonCaptor.capture(), eq(ttl));
        assertThat(objectMapper.readValue(jsonCaptor.getValue(), GuestEligibilityTicket.class)).isEqualTo(ticket);
    }

    @Test
    void findReturnsEmptyWhenKeyMissing() {
        UUID id = UUID.randomUUID();
        when(valueOps.get("guest:eligibility:" + id)).thenReturn(null);

        assertThat(store.find(id)).isEmpty();
        verify(redis, never()).delete(anyString());
    }

    @Test
    void consumeDeletesKeyAndReturnsTicket() throws Exception {
        GuestEligibilityTicket ticket = ticket();
        String key = "guest:eligibility:" + ticket.id();
        when(valueOps.get(key)).thenReturn(objectMapper.writeValueAsString(ticket));

        assertThat(store.consume(ticket.id())).contains(ticket);
        verify(redis).delete(key);
    }

    @Test
    void findLeavesKeyWhenNotConsuming() throws Exception {
        GuestEligibilityTicket ticket = ticket();
        String key = "guest:eligibility:" + ticket.id();
        when(valueOps.get(key)).thenReturn(objectMapper.writeValueAsString(ticket));

        assertThat(store.find(ticket.id())).contains(ticket);
        verify(redis, never()).delete(anyString());
    }

    @Test
    void consumeReturnsEmptyWhenJsonInvalid() {
        UUID id = UUID.randomUUID();
        String key = "guest:eligibility:" + id;
        when(valueOps.get(key)).thenReturn("{not-json");

        assertThat(store.consume(id)).isEmpty();
        verify(redis).delete(key);
    }

    @Test
    void saveThrowsWhenSerializationFails() throws Exception {
        ObjectMapper broken = org.mockito.Mockito.mock(ObjectMapper.class);
        when(broken.writeValueAsString(any())).thenThrow(new JsonProcessingException("boom") {});
        RedisGuestEligibilityTicketStore failing = new RedisGuestEligibilityTicketStore(redis, broken);

        assertThatThrownBy(() -> failing.save(ticket(), Duration.ofMinutes(1)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Unable to serialize guest ticket");
    }

    private static GuestEligibilityTicket ticket() {
        return new GuestEligibilityTicket(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), Map.of("age", 30));
    }
}
