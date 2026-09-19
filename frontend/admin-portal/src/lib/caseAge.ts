export const FALLBACK_WARNING_HOURS = 48;
export const FALLBACK_OVERDUE_HOURS = 120;

/** @deprecated use FALLBACK_WARNING_HOURS — kept for existing imports */
export const AGE_WARNING_HOURS = FALLBACK_WARNING_HOURS;
/** @deprecated use FALLBACK_OVERDUE_HOURS */
export const AGE_OVERDUE_HOURS = FALLBACK_OVERDUE_HOURS;

const OPEN_STATUSES = new Set(["CREATED", "IN_PROGRESS", "CORRECTION_REQUESTED"]);

export function isOpenStatus(status: string): boolean {
  return OPEN_STATUSES.has(status);
}

export function ageHours(createdAt: string | undefined, now = Date.now()): number | null {
  if (!createdAt) {
    return null;
  }
  const created = new Date(createdAt).getTime();
  if (Number.isNaN(created)) {
    return null;
  }
  return Math.max(0, (now - created) / 3_600_000);
}

export function warningHours(estimatedInstructionDays?: number | null): number {
  if (estimatedInstructionDays != null && estimatedInstructionDays >= 1) {
    return estimatedInstructionDays * 12;
  }
  return FALLBACK_WARNING_HOURS;
}

export function overdueHours(estimatedInstructionDays?: number | null): number {
  if (estimatedInstructionDays != null && estimatedInstructionDays >= 1) {
    return estimatedInstructionDays * 24;
  }
  return FALLBACK_OVERDUE_HOURS;
}

export type AgeTone = "ok" | "warning" | "overdue";

export function ageTone(
  hours: number | null,
  status: string,
  estimatedInstructionDays?: number | null,
): AgeTone {
  if (hours == null || !isOpenStatus(status)) {
    return "ok";
  }
  if (hours >= overdueHours(estimatedInstructionDays)) {
    return "overdue";
  }
  if (hours >= warningHours(estimatedInstructionDays)) {
    return "warning";
  }
  return "ok";
}
