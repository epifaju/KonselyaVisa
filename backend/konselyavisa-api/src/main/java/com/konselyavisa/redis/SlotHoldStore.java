package com.konselyavisa.redis;

import java.time.Duration;
import java.util.UUID;

public interface SlotHoldStore {

    boolean tryAcquire(UUID organizationId, UUID slotId, UUID caseId, int maxHolds, Duration ttl);

    void release(UUID organizationId, UUID slotId, UUID caseId);

    int activeCount(UUID organizationId, UUID slotId);
}
