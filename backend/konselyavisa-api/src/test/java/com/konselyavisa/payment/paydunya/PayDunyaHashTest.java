package com.konselyavisa.payment.paydunya;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

class PayDunyaHashTest {

    @Test
    void matchesSha512OfMasterKey() {
        String master = "wQzk9ZwR-Qq9m-0hD0-zpud-je5coGC3FHKW";
        String hash = PayDunyaHash.ofMasterKey(master);
        assertThat(hash).hasSize(128);
        assertThat(PayDunyaHash.matches(hash, master)).isTrue();
        assertThat(PayDunyaHash.matches(hash.toUpperCase(), master)).isTrue();
        assertThat(PayDunyaHash.matches(hash, "other-key")).isFalse();
        assertThat(PayDunyaHash.matches(null, master)).isFalse();
    }
}
