package com.konselyavisa.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

@ExtendWith(MockitoExtension.class)
class RedisSlotHoldStoreTest {

    @Mock
    private StringRedisTemplate redis;

    private RedisSlotHoldStore store;
    private UUID orgId;
    private UUID slotId;
    private UUID caseId;

    @BeforeEach
    void setUp() {
        store = new RedisSlotHoldStore(redis);
        orgId = UUID.randomUUID();
        slotId = UUID.randomUUID();
        caseId = UUID.randomUUID();
    }

    @Test
    void tryAcquireReturnsFalseWhenMaxHoldsBelowOne() {
        assertThat(store.tryAcquire(orgId, slotId, caseId, 0, Duration.ofMinutes(5))).isFalse();
        verifyNoInteractions(redis);
    }

    @Test
    void tryAcquireReturnsTrueWhenScriptReturnsOne() {
        when(redis.execute(any(RedisScript.class), anyList(), any(), any(), any(), any(), any())).thenReturn(1L);

        assertThat(store.tryAcquire(orgId, slotId, caseId, 2, Duration.ofSeconds(30))).isTrue();
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<String>> keys = ArgumentCaptor.forClass(List.class);
        verify(redis).execute(any(RedisScript.class), keys.capture(), any(), any(), any(), eq(caseId.toString()), any());
        assertThat(keys.getValue()).containsExactly("kv:ahc:" + orgId + ":" + slotId);
    }

    @Test
    void tryAcquireReturnsFalseWhenScriptReturnsZeroOrNull() {
        when(redis.execute(any(RedisScript.class), anyList(), any(), any(), any(), any(), any())).thenReturn(0L);
        assertThat(store.tryAcquire(orgId, slotId, caseId, 1, Duration.ofMinutes(1))).isFalse();

        when(redis.execute(any(RedisScript.class), anyList(), any(), any(), any(), any(), any())).thenReturn(null);
        assertThat(store.tryAcquire(orgId, slotId, caseId, 1, Duration.ofMinutes(1))).isFalse();
    }

    @Test
    void releaseInvokesReleaseScriptWithCaseId() {
        when(redis.execute(any(RedisScript.class), anyList(), any(), any())).thenReturn(0L);

        store.release(orgId, slotId, caseId);

        verify(redis)
                .execute(
                        any(RedisScript.class),
                        eq(List.of("kv:ahc:" + orgId + ":" + slotId)),
                        eq(caseId.toString()),
                        anyString());
    }

    @Test
    void activeCountReturnsZeroWhenScriptReturnsNull() {
        when(redis.execute(any(RedisScript.class), anyList(), any())).thenReturn(null);

        assertThat(store.activeCount(orgId, slotId)).isZero();
    }

    @Test
    void activeCountReturnsScriptCardinality() {
        when(redis.execute(any(RedisScript.class), anyList(), any())).thenReturn(3L);

        assertThat(store.activeCount(orgId, slotId)).isEqualTo(3);
    }
}
