const DEFAULT_ORG = "11111111-1111-1111-1111-111111111111";

let resolvedOrganizationId: string | null = null;

export function rememberPublicOrganizationId(organizationId: string | undefined | null) {
  if (organizationId) {
    resolvedOrganizationId = organizationId;
  }
}

export function getPublicOrganizationId(): string {
  return import.meta.env.VITE_ORGANIZATION_ID ?? resolvedOrganizationId ?? DEFAULT_ORG;
}

export function publicOrgSearchParams(): string {
  const envId = import.meta.env.VITE_ORGANIZATION_ID as string | undefined;
  if (envId) {
    return `organizationId=${encodeURIComponent(envId)}`;
  }
  if (resolvedOrganizationId) {
    return `organizationId=${encodeURIComponent(resolvedOrganizationId)}`;
  }
  const host = typeof window !== "undefined" ? window.location.hostname : "";
  if (host && host !== "localhost" && host !== "127.0.0.1") {
    return `domain=${encodeURIComponent(host)}`;
  }
  return "";
}
