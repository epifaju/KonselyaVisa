package com.konselyavisa.dossier;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class CaseSlaAgeTest {

    @Test
    void usesCatalogDaysWhenPresent() {
        assertThat(CaseSlaAge.warningHours(5)).isEqualTo(60);
        assertThat(CaseSlaAge.overdueHours(5)).isEqualTo(120);
        assertThat(CaseSlaAge.warningHours(null)).isEqualTo(48);
        assertThat(CaseSlaAge.overdueHours(null)).isEqualTo(120);
    }

    @Test
    void classifiesWatchThenOverdue() {
        Instant now = Instant.parse("2026-09-19T12:00:00Z");
        Instant watch = now.minusSeconds(70 * 3600);
        Instant overdue = now.minusSeconds(130 * 3600);
        Instant fresh = now.minusSeconds(10 * 3600);
        assertThat(CaseSlaAge.tone(fresh, CaseStatus.CREATED, 5, now)).isEqualTo(CaseSlaAge.Tone.OK);
        assertThat(CaseSlaAge.tone(watch, CaseStatus.IN_PROGRESS, 5, now)).isEqualTo(CaseSlaAge.Tone.WARNING);
        assertThat(CaseSlaAge.tone(overdue, CaseStatus.CREATED, 5, now)).isEqualTo(CaseSlaAge.Tone.OVERDUE);
        assertThat(CaseSlaAge.tone(overdue, CaseStatus.COMPLETED, 5, now)).isEqualTo(CaseSlaAge.Tone.OK);
    }
}
