package com.konselyavisa.dossier;

import java.time.Duration;
import java.time.Instant;

public final class CaseSlaAge {

    public static final int FALLBACK_WARNING_HOURS = 48;
    public static final int FALLBACK_OVERDUE_HOURS = 120;

    public enum Tone {
        OK,
        WARNING,
        OVERDUE
    }

    private CaseSlaAge() {}

    public static boolean isOpen(CaseStatus status) {
        return status == CaseStatus.CREATED
                || status == CaseStatus.IN_PROGRESS
                || status == CaseStatus.CORRECTION_REQUESTED;
    }

    public static long warningHours(Integer estimatedInstructionDays) {
        if (estimatedInstructionDays != null && estimatedInstructionDays >= 1) {
            return estimatedInstructionDays * 12L;
        }
        return FALLBACK_WARNING_HOURS;
    }

    public static long overdueHours(Integer estimatedInstructionDays) {
        if (estimatedInstructionDays != null && estimatedInstructionDays >= 1) {
            return estimatedInstructionDays * 24L;
        }
        return FALLBACK_OVERDUE_HOURS;
    }

    public static double ageHours(Instant createdAt, Instant now) {
        if (createdAt == null || now == null) {
            return 0;
        }
        return Math.max(0d, Duration.between(createdAt, now).toMillis() / 3_600_000d);
    }

    public static Tone tone(Instant createdAt, CaseStatus status, Integer estimatedInstructionDays, Instant now) {
        if (!isOpen(status) || createdAt == null) {
            return Tone.OK;
        }
        double hours = ageHours(createdAt, now);
        if (hours >= overdueHours(estimatedInstructionDays)) {
            return Tone.OVERDUE;
        }
        if (hours >= warningHours(estimatedInstructionDays)) {
            return Tone.WARNING;
        }
        return Tone.OK;
    }
}
