package com.konselyavisa.outbox;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class OutboxBackoffTest {

    @Test
    void doublesUntilMax() {
        Duration initial = Duration.ofSeconds(2);
        Duration max = Duration.ofSeconds(10);
        assertThat(OutboxBackoff.delay(1, initial, max)).isEqualTo(Duration.ofSeconds(2));
        assertThat(OutboxBackoff.delay(2, initial, max)).isEqualTo(Duration.ofSeconds(4));
        assertThat(OutboxBackoff.delay(3, initial, max)).isEqualTo(Duration.ofSeconds(8));
        assertThat(OutboxBackoff.delay(4, initial, max)).isEqualTo(Duration.ofSeconds(10));
    }
}
