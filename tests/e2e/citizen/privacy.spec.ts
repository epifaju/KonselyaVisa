import { expect, test } from "@playwright/test";
import { fulfillJson, mockMe, mockOidc, mockPublicSite, ok, seedOidcUser } from "../../fixtures/api";

test.beforeEach(async ({ page }) => {
  await mockOidc(page);
  await mockPublicSite(page);
});

test("la page publique de confidentialité affiche la notice", async ({ page }) => {
  await page.goto("/");
  await page.getByRole("button", { name: "Confidentialité" }).click();
  await expect(page.getByRole("heading", { name: "Confidentialité" })).toBeVisible();
  await expect(page.getByText("Notice de test.")).toBeVisible();
});

test("un citoyen exporte ses données et demande l’anonymisation", async ({ page }) => {
  await seedOidcUser(page, { clientId: "citizen-portal", roles: ["CITIZEN"], name: "Awa Diallo" });
  await mockMe(page, ["CITIZEN"]);
  await page.route("**/api/v1/cases**", (route) =>
    fulfillJson(route, { content: [], totalElements: 0, page: 0, size: 50, totalPages: 0, first: true, last: true }),
  );
  await page.route("**/api/v1/me/data-export", (route) => fulfillJson(route, ok({ subject: "user-1" })));
  await page.route("**/api/v1/me/data-deletion-request", (route) =>
    fulfillJson(route, ok({
      id: "55555555-5555-5555-5555-555555555555",
      status: "SCHEDULED",
      requestedAt: "2026-10-28T10:00:00Z",
      scheduledAnonymizeAt: "2036-10-28T10:00:00Z",
    })),
  );

  await page.goto("/");
  await page.getByRole("button", { name: /Awa Diallo/ }).click();
  await page.getByRole("menuitem", { name: "Mes données" }).click();
  await expect(page.getByRole("heading", { name: "Mes données" })).toBeVisible();
  await page.getByRole("button", { name: "Télécharger mes données" }).click();
  await expect(page.getByText("Fichier d’export téléchargé.")).toBeVisible();
  await page.getByRole("button", { name: "Demander l’anonymisation" }).click();
  await expect(page.getByText(/Anonymisation planifiée le 2036-10-28/)).toBeVisible();
});
