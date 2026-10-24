import { describe, expect, it } from "vitest";
import { flagEmoji } from "./flagEmoji";

describe("flagEmoji", () => {
  it("maps ISO-2 to regional indicator symbols", () => {
    expect(flagEmoji("fr")).toBe("🇫🇷");
    expect(flagEmoji(" GW ")).toBe("🇬🇼");
  });

  it("rejects invalid codes", () => {
    expect(flagEmoji("")).toBe("");
    expect(flagEmoji("FRA")).toBe("");
    expect(flagEmoji("1A")).toBe("");
  });
});
