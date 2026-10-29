import type { Page, Route } from "@playwright/test";

export const ORG_ID = "11111111-1111-1111-1111-111111111111";
export const PROCEDURE_ID = "22222222-2222-2222-2222-222222222222";
export const CASE_ID = "33333333-3333-3333-3333-333333333333";
export const DOCUMENT_ID = "44444444-4444-4444-4444-444444444444";

const AUTHORITY = "http://localhost:8081/realms/konselyavisa";

export const oidcDiscovery = {
  issuer: AUTHORITY,
  authorization_endpoint: `${AUTHORITY}/protocol/openid-connect/auth`,
  token_endpoint: `${AUTHORITY}/protocol/openid-connect/token`,
  end_session_endpoint: `${AUTHORITY}/protocol/openid-connect/logout`,
  jwks_uri: `${AUTHORITY}/protocol/openid-connect/certs`,
  response_types_supported: ["code"],
  subject_types_supported: ["public"],
  id_token_signing_alg_values_supported: ["RS256"],
};

const organization = {
  organizationId: ORG_ID,
  nameI18n: { fr: "Consulat test", pt: "Consulado teste", en: "Test consulate" },
  defaultLocale: "fr",
  activeLanguages: ["fr", "pt", "en"],
  brandColor: "#0B3D2E",
  domain: null,
  logoUrl: null,
  faviconUrl: null,
};

export function ok<T>(data: T) {
  return { success: true, data };
}

export async function fulfillJson(route: Route, body: unknown, status = 200) {
  await route.fulfill({ status, contentType: "application/json", json: body });
}

export async function mockOidc(page: Page) {
  await page.route("**/realms/konselyavisa/.well-known/openid-configuration", (route) =>
    fulfillJson(route, oidcDiscovery),
  );
  await page.route("**/protocol/openid-connect/auth**", (route) =>
    route.fulfill({ status: 200, contentType: "text/html", body: "<main>oidc-auth</main>" }),
  );
  await page.route("**/protocol/openid-connect/logout**", (route) =>
    route.fulfill({ status: 200, contentType: "text/html", body: "<main>oidc-logout</main>" }),
  );
}

export async function mockPublicSite(page: Page) {
  await page.route("**/api/v1/public/org**", (route) => fulfillJson(route, ok(organization)));
  await page.route("**/api/v1/public/site**", (route) =>
    fulfillJson(route, ok({
      ...organization,
      addressI18n: { fr: "1 rue du Test" },
      openingHoursI18n: { fr: "9h-16h" },
      contactEmail: "contact@example.test",
      contactPhone: null,
      formalities: [
        { category: "VISA", nameI18n: { fr: "Visa" } },
        { category: "LEGALIZATION", nameI18n: { fr: "Légalisation" } },
      ],
    })),
  );
  await page.route("**/api/v1/privacy-notices/current**", (route) =>
    fulfillJson(route, ok({ version: "1", textI18n: { fr: "Notice de test." } })),
  );
}

export function visaProcedure() {
  return {
    id: PROCEDURE_ID,
    code: "VISA-GW",
    category: "VISA",
    nameI18n: { fr: "Visa tourisme" },
    descriptionI18n: { fr: "Séjour touristique" },
    originCountry: { isoCode: "FR", nameI18n: { fr: "France" } },
    destinationCountry: { isoCode: "GW", nameI18n: { fr: "Guinée-Bissau" } },
    publishedVersionNumber: 1,
    versions: [
      {
        id: "55555555-5555-5555-5555-555555555555",
        versionNumber: 1,
        status: "PUBLISHED",
        documentRequirements: [{ code: "PASSPORT", required: true, labelI18n: { fr: "Passeport" } }],
        pricing: { currency: "EUR", amountMinor: 5000 },
        estimatedInstructionDays: 10,
      },
    ],
  };
}

export async function mockProcedures(page: Page) {
  await page.route("**/api/v1/public/procedures**", (route) =>
    fulfillJson(route, { content: [visaProcedure()], totalElements: 1 }),
  );
  await page.route("**/api/v1/procedures**", (route) =>
    fulfillJson(route, { content: [visaProcedure()], totalElements: 1 }),
  );
}

export async function seedOidcUser(
  page: Page,
  options: { clientId: string; roles: string[]; name: string; expired?: boolean },
) {
  const key = `oidc.user:${AUTHORITY}:${options.clientId}`;
  const expiresAt = options.expired ? 1 : 4_102_444_800;
  const user = {
    id_token: "id-token",
    session_state: "session",
    access_token: "test-access-token",
    refresh_token: "test-refresh-token",
    token_type: "Bearer",
    scope: "openid profile email",
    profile: {
      sub: "user-1",
      iss: AUTHORITY,
      aud: options.clientId,
      exp: expiresAt,
      iat: 1,
      name: options.name,
      preferred_username: options.name,
      realm_access: { roles: options.roles },
    },
    expires_at: expiresAt,
  };
  await page.addInitScript(
    ([storageKey, stored]) => {
      window.sessionStorage.setItem(storageKey, stored);
    },
    [key, JSON.stringify(user)] as const,
  );
}

export async function mockMe(page: Page, roles: string[]) {
  await page.route("**/api/v1/me", (route) =>
    fulfillJson(route, ok({
      username: "user-1",
      roles,
      organizationId: ORG_ID,
      organization,
    })),
  );
}
