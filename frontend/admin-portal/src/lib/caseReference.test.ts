import { describe, expect, it } from "vitest";
import { shortCaseReference } from "./caseReference";

describe("shortCaseReference", () => {
  it("returns empty for missing reference", () => {
    expect(shortCaseReference(undefined)).toBe("");
  });

  it("formats prefix and digit tail", () => {
    expect(shortCaseReference("KV-2026-ZZ99AA12")).toBe("KV-9912");
  });
});
