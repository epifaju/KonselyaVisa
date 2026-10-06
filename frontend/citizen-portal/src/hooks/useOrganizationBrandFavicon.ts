import { useEffect } from "react";

const API_BASE = import.meta.env.VITE_API_BASE_URL ?? "";

/** Sets document favicon from organization branding (public API path). */
export function useOrganizationBrandFavicon(faviconUrl: string | null | undefined) {
  useEffect(() => {
    const href = faviconUrl ? `${API_BASE}${faviconUrl}` : null;
    let link = document.querySelector<HTMLLinkElement>("link[rel='icon'][data-org-brand='true']");
    if (!href) {
      link?.remove();
      return;
    }
    if (!link) {
      link = document.createElement("link");
      link.rel = "icon";
      link.dataset.orgBrand = "true";
      document.head.appendChild(link);
    }
    link.href = href;
    return () => {
      link?.remove();
    };
  }, [faviconUrl]);
}
