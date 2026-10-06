package com.konselyavisa.outbox;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class OutboxChecklistPayloadTest {

    @Test
    void keepsOnlyCodeRequiredAndLabels() {
        List<Map<String, Object>> checklist = OutboxChecklistPayload.fromRequirements(List.of(Map.of(
                "code",
                "PASSPORT",
                "required",
                true,
                "labelI18n",
                Map.of("fr", "Passeport", "en", "Passport"),
                "constraints",
                Map.of("minValidityMonths", 6))));

        assertThat(checklist).hasSize(1);
        assertThat(checklist.getFirst())
                .containsEntry("code", "PASSPORT")
                .containsEntry("required", true)
                .containsKey("labelI18n")
                .doesNotContainKey("constraints");
    }

    @Test
    void skipsBlankCodes() {
        assertThat(OutboxChecklistPayload.fromRequirements(List.of(Map.of("code", "  "), Map.of("labelI18n", Map.of()))))
                .isEmpty();
    }
}
