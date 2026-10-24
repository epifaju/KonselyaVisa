/**
 * WCAG contrast audit for KonselyaVisa design tokens.
 * Run from repo root: node frontend/scripts/contrast-audit.mjs
 */
import { DARK, LIGHT } from "../../packages/design-tokens/src/palette.js";

function hslToRgb(h, s, l) {
  s /= 100;
  l /= 100;
  const a = s * Math.min(l, 1 - l);
  const f = (n) => {
    const k = (n + h / 30) % 12;
    const c = l - a * Math.max(Math.min(k - 3, 9 - k, 1), -1);
    return Math.round(255 * c);
  };
  return [f(0), f(8), f(4)];
}

function rgbToHex([r, g, b]) {
  return `#${[r, g, b].map((x) => x.toString(16).padStart(2, "0")).join("")}`;
}

function srgb(c) {
  c /= 255;
  return c <= 0.03928 ? c / 12.92 : ((c + 0.055) / 1.055) ** 2.4;
}

function rel(hex) {
  const v = hex.slice(1);
  return (
    0.2126 * srgb(parseInt(v.slice(0, 2), 16)) +
    0.7152 * srgb(parseInt(v.slice(2, 4), 16)) +
    0.0722 * srgb(parseInt(v.slice(4, 6), 16))
  );
}

function contrast(a, b) {
  const A = rel(a);
  const B = rel(b);
  return (Math.max(A, B) + 0.05) / (Math.min(A, B) + 0.05);
}

function mix(fgHex, bgHex, alpha) {
  const f = [1, 3, 5].map((i) => parseInt(fgHex.slice(i, i + 2), 16));
  const b = [1, 3, 5].map((i) => parseInt(bgHex.slice(i, i + 2), 16));
  return rgbToHex(f.map((c, i) => Math.round(c * alpha + b[i] * (1 - alpha))));
}

function hsl(triplet) {
  return rgbToHex(hslToRgb(...triplet));
}

const WHITE = "#ffffff";
const DARK_FG = "#161b26";
const CARD = "#ffffff";
const BG = hsl(LIGHT.background);
const BLACK = "#000000";

function fgFor(fill) {
  return contrast(fill, WHITE) >= contrast(fill, DARK_FG) ? WHITE : DARK_FG;
}

const light = {
  primary: hsl(LIGHT.primary),
  success: hsl(LIGHT.success),
  warning: hsl(LIGHT.warning),
  destructive: hsl(LIGHT.destructive),
  nav: hsl(LIGHT.nav),
  mutedFg: hsl(LIGHT.mutedForeground),
};

const dark = {
  primary: hsl(DARK.primary),
  success: hsl(DARK.success),
  warning: hsl(DARK.warning),
  destructive: hsl(DARK.destructive),
};

const pairs = [];

function addSolid(theme, name, fill) {
  pairs.push([`${theme} solid ${name}`, fill, fgFor(fill), 4.5]);
}

function addText(theme, name, fill, page) {
  pairs.push([`${theme} text-${name} on page`, fill, page, 4.5]);
  pairs.push([`${theme} text-${name} on soft/10`, fill, mix(fill, page, 0.1), 4.5]);
}

for (const [name, fill] of Object.entries(light)) {
  if (name === "mutedFg") continue;
  addSolid("light", name, fill);
  addText("light", name, fill, CARD);
  addText("light", `${name}/bg`, fill, BG);
}
pairs.push(["light muted-fg on bg", light.mutedFg, BG, 4.5]);
pairs.push(["light muted-fg on card", light.mutedFg, CARD, 4.5]);

for (const [name, fill] of Object.entries(dark)) {
  addSolid("dark", name, fill);
  addText("dark", name, fill, BLACK);
}

let fails = 0;
for (const [label, a, b, min] of pairs) {
  const ratio = contrast(a, b);
  const ok = ratio >= min;
  if (!ok) fails += 1;
  console.log(`${ok ? "OK" : "FAIL"}\t${ratio.toFixed(2)}\t(need ${min})\t${label}`);
}

console.log(`\n${fails === 0 ? "PASS" : "FAIL"} — ${pairs.length - fails}/${pairs.length} pairs ≥ AA`);
process.exit(fails ? 1 : 0);
