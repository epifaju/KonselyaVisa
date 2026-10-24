import { describe, expect, it } from "vitest";
import { cn } from "./utils";

describe("cn", () => {
  it("merges conflicting utilities", () => {
    expect(cn("py-1", "py-3")).toBe("py-3");
  });

  it("preserves nav-foreground token against generic text classes", () => {
    expect(cn("text-nav-foreground", "text-body-sm")).toContain("text-nav-foreground");
  });
});
