package com.konselyavisa.redis;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class NoOpSlotHoldStoreTest {

    private final NoOpSlotHoldStore store = new NoOpSlotHoldStore();

    @Test
    void tryAcquireSucceedsWhenMaxHoldsPositive() {
        assertThat(store.tryAcquire(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 2, Duration.ofMinutes(1)))
                .isTrue();
    }

    @Test
    void tryAcquireFailsWhenMaxHoldsZero() {
        assertThat(store.tryAcquire(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 0, Duration.ofMinutes(1)))
                .isFalse();
    }

    @Test
    void activeCountAlwaysZeroAndReleaseIsNoOp() {
        UUID orgId = UUID.randomUUID();
        UUID slotId = UUID.randomUUID();
        store.release(orgId, slotId, UUID.randomUUID());
        assertThat(store.activeCount(orgId, slotId)).isZero();
    }
}
