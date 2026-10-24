import { describe, expect, it } from "vitest";
import { cn } from "./utils";

describe("cn", () => {
  it("merges conflicting utility classes", () => {
    expect(cn("px-2", "px-4")).toBe("px-4");
  });

  it("keeps custom primary-foreground text token", () => {
    expect(cn("text-primary-foreground", "text-body")).toContain("text-primary-foreground");
  });
});
