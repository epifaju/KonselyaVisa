import { useEffect } from "react";
import { hexToHslComponents, primaryForegroundForHex } from "@/lib/brandColor";

export function useOrganizationBrandTheme(brandColor: string | null | undefined) {
  useEffect(() => {
    const root = document.documentElement;
    const clear = () => {
      root.style.removeProperty("--org-primary");
      root.style.removeProperty("--org-primary-foreground");
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
    root.style.setProperty("--primary", hsl);
    root.style.setProperty("--primary-foreground", foreground);
    root.style.setProperty("--ring", hsl);
    return clear;
  }, [brandColor]);
}
