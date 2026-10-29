import { expect, test } from "@playwright/test";
import { mockMe, mockOidc, mockPublicSite, seedOidcUser } from "../../fixtures/api";

test.beforeEach(async ({ page }) => {
  await mockOidc(page);
  await mockPublicSite(page);
});

test("le catalogue et les paramètres restent cachés pour un agent", async ({ page }) => {
  await seedOidcUser(page, { clientId: "agent-portal", roles: ["AGENT"], name: "Agent Test" });
  await mockMe(page, ["AGENT"]);
  await page.route("**/api/v1/agent/cases**", (route) =>
    route.fulfill({
      json: { content: [], totalElements: 0, page: 0, size: 50, totalPages: 0, first: true, last: true },
    }),
  );
  await page.goto("/");
  await expect(page.getByRole("button", { name: "Dossiers" })).toBeVisible();
  await expect(page.getByRole("button", { name: "Créneaux" })).toBeVisible();
  await expect(page.getByRole("button", { name: "Catalogue" })).toHaveCount(0);
  await expect(page.getByRole("button", { name: "Organisation" })).toHaveCount(0);
});

test("un administrateur métier voit le catalogue", async ({ page }) => {
  await seedOidcUser(page, { clientId: "agent-portal", roles: ["BUSINESS_ADMIN"], name: "Admin Test" });
  await mockMe(page, ["BUSINESS_ADMIN"]);
  await page.route("**/api/v1/agent/cases**", (route) =>
    route.fulfill({
      json: { content: [], totalElements: 0, page: 0, size: 50, totalPages: 0, first: true, last: true },
    }),
  );
  await page.route("**/api/v1/supervisor/summary**", (route) =>
    route.fulfill({ json: { success: true, data: { openCount: 0, watchCount: 0, overdueCount: 0 } } }),
  );
  await page.goto("/");
  await expect(page.getByRole("button", { name: "Catalogue" })).toBeVisible();
  await expect(page.getByRole("button", { name: "Organisation" })).toBeVisible();
});
