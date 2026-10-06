export function rolesFromSession(meRoles: string[] | undefined, profile?: unknown): string[] {
  const realmAccess =
    profile && typeof profile === "object" && "realm_access" in profile
      ? (profile as { realm_access?: { roles?: unknown } }).realm_access
      : undefined;
  const fromRealm = Array.isArray(realmAccess?.roles)
    ? realmAccess.roles.filter((role): role is string => typeof role === "string")
    : [];
  return [...new Set([...(meRoles ?? []), ...fromRealm])];
}

export function isConsularStaff(roles: string[] | undefined): boolean {
  const normalized = (roles ?? []).map((role) => role.replace(/^ROLE_/, ""));
  if (normalized.includes("COMPANY_USER")) {
    return false;
  }
  return (
    ["AGENT", "SUPERVISOR", "PLATFORM_ADMIN"].some((role) => normalized.includes(role)) ||
    (normalized.includes("BUSINESS_ADMIN") && !normalized.includes("CITIZEN"))
  );
}
