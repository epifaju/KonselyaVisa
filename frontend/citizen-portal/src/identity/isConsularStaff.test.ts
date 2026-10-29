import { describe, expect, it } from "vitest";
import { isConsularStaff, rolesFromSession } from "./isConsularStaff";

describe("rolesFromSession", () => {
  it("merges me roles and realm roles without duplicates", () => {
    expect(
      rolesFromSession(["CITIZEN"], {
        realm_access: { roles: ["CITIZEN", "offline_access", 1] },
      }),
    ).toEqual(["CITIZEN", "offline_access"]);
  });

  it("ignores a malformed profile", () => {
    expect(rolesFromSession(undefined, null)).toEqual([]);
    expect(rolesFromSession(["AGENT"], { realm_access: { roles: "AGENT" } })).toEqual(["AGENT"]);
  });
});

describe("isConsularStaff", () => {
  it("accepts consular roles and strips the ROLE_ prefix", () => {
    expect(isConsularStaff(["ROLE_AGENT"])).toBe(true);
    expect(isConsularStaff(["SUPERVISOR"])).toBe(true);
    expect(isConsularStaff(["PLATFORM_ADMIN"])).toBe(true);
    expect(isConsularStaff(["BUSINESS_ADMIN"])).toBe(true);
  });

  it("keeps citizens and company users on the citizen portal", () => {
    expect(isConsularStaff(["CITIZEN"])).toBe(false);
    expect(isConsularStaff(["COMPANY_USER"])).toBe(false);
    expect(isConsularStaff(["COMPANY_USER", "AGENT"])).toBe(false);
    expect(isConsularStaff(["BUSINESS_ADMIN", "CITIZEN"])).toBe(false);
    expect(isConsularStaff(undefined)).toBe(false);
    expect(isConsularStaff([])).toBe(false);
  });
});
