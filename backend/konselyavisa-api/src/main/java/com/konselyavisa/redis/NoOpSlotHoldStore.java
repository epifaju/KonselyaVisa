package com.konselyavisa.redis;

import java.time.Duration;
import java.util.UUID;

public class NoOpSlotHoldStore implements SlotHoldStore {

    @Override
    public boolean tryAcquire(UUID organizationId, UUID slotId, UUID caseId, int maxHolds, Duration ttl) {
        return maxHolds > 0;
    }

    @Override
    public void release(UUID organizationId, UUID slotId, UUID caseId) {
        // no-op
    }

    @Override
    public int activeCount(UUID organizationId, UUID slotId) {
        return 0;
    }
}
