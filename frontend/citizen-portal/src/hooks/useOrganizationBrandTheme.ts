import { useEffect } from "react";
import { brandSurfaceFromHex, hexToHslComponents, primaryForegroundForHex } from "@/lib/brandColor";

/** Applies organization `brandColor` onto design-token CSS variables (`--org-primary`). */
export function useOrganizationBrandTheme(brandColor: string | null | undefined) {
  useEffect(() => {
    const root = document.documentElement;
    const clear = () => {
      root.style.removeProperty("--org-primary");
      root.style.removeProperty("--org-primary-foreground");
      root.style.removeProperty("--org-background");
      root.style.removeProperty("--org-muted");
      root.style.removeProperty("--primary");
      root.style.removeProperty("--primary-foreground");
      root.style.removeProperty("--ring");
    };
    if (!brandColor) {
      clear();
      return;
    }
    const hsl = hexToHslComponents(brandColor);
    if (!hsl) {
      return;
    }
    const foreground = primaryForegroundForHex(brandColor);
    root.style.setProperty("--org-primary", hsl);
    root.style.setProperty("--org-primary-foreground", foreground);
    // Set tokens directly so hsl(var(--primary-foreground)) cannot resolve to inherited --foreground.
    root.style.setProperty("--primary", hsl);
    root.style.setProperty("--primary-foreground", foreground);
    root.style.setProperty("--ring", hsl);
    const background = brandSurfaceFromHex(brandColor, 96);
    const muted = brandSurfaceFromHex(brandColor, 93);
    if (background) {
      root.style.setProperty("--org-background", background);
    }
    if (muted) {
      root.style.setProperty("--org-muted", muted);
    }
    return clear;
  }, [brandColor]);
}
