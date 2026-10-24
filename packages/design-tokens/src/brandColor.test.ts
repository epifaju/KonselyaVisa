import { describe, expect, it } from "vitest";
import {
  PUBLIC_PRIMARY_HEX,
  brandSurfaceFromHex,
  contrastRatio,
  hexToHslComponents,
  primaryForegroundHexForBrand,
  relativeLuminance,
} from "./brandColor.js";

describe("brandColor", () => {
  it("hexToHslComponents parses public primary and rejects garbage", () => {
    const hsl = hexToHslComponents(PUBLIC_PRIMARY_HEX);
    expect(hsl).toMatch(/^\d+ \d+% \d+%$/);
    expect(hexToHslComponents("not-a-color")).toBeNull();
    expect(hexToHslComponents("#abc")).toBeNull();
  });

  it("contrastRatio black/white is ~21 and brand CTA uses white foreground", () => {
    expect(contrastRatio("#000000", "#FFFFFF")).toBeCloseTo(21, 0);
    expect(relativeLuminance("#FFFFFF")).toBeCloseTo(1, 5);
    expect(primaryForegroundHexForBrand(PUBLIC_PRIMARY_HEX)).toBe("#FFFFFF");
  });

  it("brandSurfaceFromHex softens saturation for page wash", () => {
    const surface = brandSurfaceFromHex(PUBLIC_PRIMARY_HEX, 97);
    expect(surface).toMatch(/^\d+ \d+% 97%$/);
    expect(brandSurfaceFromHex("nope", 97)).toBeNull();
  });
});
