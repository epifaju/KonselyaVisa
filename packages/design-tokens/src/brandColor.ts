/** Public default brand hex (matches CSS `--primary` light fallback). */
export const PUBLIC_PRIMARY_HEX = "#0B61CB";

/** Convert `#RRGGBB` to CSS HSL components (`H S% L%`) for design tokens. */
export function hexToHslComponents(hex: string): string | null {
  const match = /^#([0-9a-f]{6})$/i.exec(hex.trim());
  if (!match) {
    return null;
  }
  const value = match[1];
  const r = parseInt(value.slice(0, 2), 16) / 255;
  const g = parseInt(value.slice(2, 4), 16) / 255;
  const b = parseInt(value.slice(4, 6), 16) / 255;
  const max = Math.max(r, g, b);
  const min = Math.min(r, g, b);
  const l = (max + min) / 2;
  let h = 0;
  let s = 0;
  if (max !== min) {
    const d = max - min;
    s = l > 0.5 ? d / (2 - max - min) : d / (max + min);
    switch (max) {
      case r:
        h = ((g - b) / d + (g < b ? 6 : 0)) / 6;
        break;
      case g:
        h = ((b - r) / d + 2) / 6;
        break;
      default:
        h = ((r - g) / d + 4) / 6;
    }
  }
  const H = Math.round(h * 360);
  const S = Math.round(s * 100);
  const L = Math.round(l * 100);
  return `${H} ${S}% ${L}%`;
}

function srgbChannel(slice: string): number {
  const c = parseInt(slice, 16) / 255;
  return c <= 0.03928 ? c / 12.92 : ((c + 0.055) / 1.055) ** 2.4;
}

/** Relative luminance (WCAG) for `#RRGGBB`. */
export function relativeLuminance(hex: string): number | null {
  const match = /^#([0-9a-f]{6})$/i.exec(hex.trim());
  if (!match) {
    return null;
  }
  const value = match[1];
  return (
    0.2126 * srgbChannel(value.slice(0, 2)) +
    0.7152 * srgbChannel(value.slice(2, 4)) +
    0.0722 * srgbChannel(value.slice(4, 6))
  );
}

/** WCAG contrast ratio between two `#RRGGBB` colors. */
export function contrastRatio(hexA: string, hexB: string): number | null {
  const a = relativeLuminance(hexA);
  const b = relativeLuminance(hexB);
  if (a == null || b == null) {
    return null;
  }
  const lighter = Math.max(a, b);
  const darker = Math.min(a, b);
  return (lighter + 0.05) / (darker + 0.05);
}

const FG_WHITE = "#FFFFFF";
const FG_DARK = "#161B26";

export function primaryForegroundForHex(hex: string): string {
  const white = contrastRatio(hex, FG_WHITE);
  const dark = contrastRatio(hex, FG_DARK);
  if (white == null || dark == null) {
    return "0 0% 100%";
  }
  return white >= dark ? "0 0% 100%" : "222 30% 12%";
}

/** Resolve CTA text hex used with a brand fill (for contrast smoke checks). */
export function primaryForegroundHexForBrand(hex: string): string {
  return primaryForegroundForHex(hex) === "0 0% 100%" ? FG_WHITE : FG_DARK;
}

/** Light page wash derived from brand hue (for `.login-theme` background / muted). */
export function brandSurfaceFromHex(hex: string, lightness: number): string | null {
  const hsl = hexToHslComponents(hex);
  if (!hsl) {
    return null;
  }
  const [hRaw, sRaw] = hsl.split(" ");
  const h = Number.parseInt(hRaw, 10);
  const s = Number.parseInt(sRaw.replace("%", ""), 10);
  if (Number.isNaN(h) || Number.isNaN(s)) {
    return null;
  }
  const softS = Math.min(Math.max(s, 18), 48);
  return `${h} ${softS}% ${lightness}%`;
}
