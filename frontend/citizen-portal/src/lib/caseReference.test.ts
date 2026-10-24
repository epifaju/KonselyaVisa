import { describe, expect, it } from "vitest";
import { shortCaseReference } from "./caseReference";

describe("shortCaseReference", () => {
  it("returns empty for missing reference", () => {
    expect(shortCaseReference(undefined)).toBe("");
    expect(shortCaseReference("")).toBe("");
  });

  it("keeps letter prefix and last four digits", () => {
    expect(shortCaseReference("KV-2026-AB12CD34")).toBe("KV-1234");
    expect(shortCaseReference("ord-9")).toBe("ORD-0009");
  });
});
