package com.konselyavisa.outbox;

import java.time.Duration;

public final class OutboxBackoff {

    private OutboxBackoff() {}

    public static Duration delay(int attempts, Duration initial, Duration max) {
        int safeAttempts = Math.max(attempts, 1);
        int shift = Math.min(safeAttempts - 1, 16);
        Duration computed = initial.multipliedBy(1L << shift);
        if (computed.compareTo(max) > 0) {
            return max;
        }
        return computed;
    }
}
