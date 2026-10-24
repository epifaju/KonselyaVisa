import { describe, expect, it } from "vitest";
import {
  ageHours,
  ageTone,
  isOpenStatus,
  overdueHours,
  warningHours,
} from "./caseAge";

describe("caseAge", () => {
  it("detects open statuses", () => {
    expect(isOpenStatus("IN_PROGRESS")).toBe(true);
    expect(isOpenStatus("COMPLETED")).toBe(false);
  });

  it("computes age hours and thresholds from estimated days", () => {
    const now = Date.parse("2026-10-23T12:00:00Z");
    expect(ageHours("2026-10-23T00:00:00Z", now)).toBe(12);
    expect(ageHours("not-a-date", now)).toBeNull();
    expect(warningHours(2)).toBe(24);
    expect(overdueHours(2)).toBe(48);
    expect(warningHours(null)).toBe(48);
    expect(overdueHours(undefined)).toBe(120);
  });

  it("maps tone for open cases", () => {
    expect(ageTone(10, "CREATED", 2)).toBe("ok");
    expect(ageTone(30, "IN_PROGRESS", 2)).toBe("warning");
    expect(ageTone(50, "CORRECTION_REQUESTED", 2)).toBe("overdue");
    expect(ageTone(200, "COMPLETED", 2)).toBe("ok");
  });
});
