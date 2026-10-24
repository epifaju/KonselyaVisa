/**
 * Canonical HSL token values (light / dark) — source for CSS and contrast-audit.
 * Components: [H, S, L] with S/L as percent numbers.
 */
export const LIGHT = {
  background: [213, 48, 97],
  foreground: [222, 30, 12],
  primary: [213, 90, 42],
  primaryForeground: [0, 0, 100],
  nav: [213, 90, 42],
  navForeground: [0, 0, 100],
  success: [142, 76, 26],
  successForeground: [0, 0, 100],
  warning: [38, 90, 28],
  warningForeground: [0, 0, 100],
  destructive: [0, 72, 44],
  destructiveForeground: [0, 0, 100],
  mutedForeground: [215, 12, 40],
};

export const DARK = {
  background: [0, 0, 0],
  foreground: [0, 0, 93],
  primary: [213, 85, 55],
  primaryForeground: [222, 30, 12],
  nav: [213, 85, 55],
  navForeground: [222, 30, 12],
  success: [142, 62, 45],
  successForeground: [222, 30, 12],
  warning: [38, 92, 58],
  warningForeground: [222, 30, 12],
  destructive: [0, 70, 62],
  destructiveForeground: [222, 30, 12],
};

/** Keep in sync with brandColor.ts PUBLIC_PRIMARY_HEX. */
export const PUBLIC_PRIMARY_HEX = "#0B61CB";
