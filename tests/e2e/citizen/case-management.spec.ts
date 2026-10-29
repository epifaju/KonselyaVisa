import { expect, test } from "@playwright/test";
import { fulfillJson, mockMe, mockOidc, mockPublicSite, seedOidcUser } from "../../fixtures/api";

test.beforeEach(async ({ page }) => {
  await mockOidc(page);
  await mockPublicSite(page);
});

test("le suivi affiche une erreur API sans session", async ({ page }) => {
  await page.route("**/api/v1/public/case-status", (route) =>
    fulfillJson(route, { success: false, messageKey: "error.case.not_found" }, 404),
  );
  await page.goto("/");
  await page.getByRole("button", { name: "Suivre un dossier existant" }).click();
  await expect(page.getByRole("heading", { name: "Suivre un dossier" })).toBeVisible();
  await page.getByLabel("Référence du dossier").fill("KV-2026-AB12CD34");
  await page.getByLabel("E-mail du demandeur").fill("inconnu@example.test");
  await page.getByRole("button", { name: "Rechercher" }).click();
  await expect(page.getByRole("alert")).toContainText("Aucun dossier ne correspond");
});

test("un citoyen connecté voit une liste vide puis une erreur API", async ({ page }) => {
  await seedOidcUser(page, { clientId: "citizen-portal", roles: ["CITIZEN"], name: "Awa Diallo" });
  await mockMe(page, ["CITIZEN"]);
  await page.route("**/api/v1/cases**", (route) =>
    fulfillJson(route, { content: [], totalElements: 0, page: 0, size: 50, totalPages: 0, first: true, last: true }),
  );

  await page.goto("/");
  await expect(page.getByRole("heading", { name: "Mes dossiers" })).toBeVisible();
  await expect(page.getByText("Aucun dossier pour le moment.")).toBeVisible();

  await page.unroute("**/api/v1/cases**");
  await page.route("**/api/v1/cases**", (route) =>
    fulfillJson(route, { success: false, messageKey: "error.http" }, 500),
  );
  await page.getByRole("button", { name: "Nouvelle démarche" }).click();
  await page.getByRole("button", { name: "Quitter" }).click();
  await expect(page.getByRole("alert")).toContainText("L’API a renvoyé une erreur.");
  await expect(page.getByRole("button", { name: "Réessayer" })).toBeVisible();
});

test("une session expirée revient à l’accueil public", async ({ page }) => {
  await page.route("**/localhost:8081/**", (route) => route.abort());
  await seedOidcUser(page, {
    clientId: "citizen-portal",
    roles: ["CITIZEN"],
    name: "Awa Diallo",
    expired: true,
  });
  await page.goto("/");
  await expect(page.getByRole("heading", { name: "Vos démarches consulaires, simplifiées" })).toBeVisible();
  await expect(page.getByRole("heading", { name: "Mes dossiers" })).toHaveCount(0);
});

test("la déconnexion interroge la fin de session", async ({ page }) => {
  await seedOidcUser(page, { clientId: "citizen-portal", roles: ["CITIZEN"], name: "Awa Diallo" });
  await mockMe(page, ["CITIZEN"]);
  await page.route("**/api/v1/cases**", (route) =>
    fulfillJson(route, { content: [], totalElements: 0, page: 0, size: 50, totalPages: 0, first: true, last: true }),
  );
  await page.goto("/");
  await expect(page.getByRole("heading", { name: "Mes dossiers" })).toBeVisible();
  const logout = page.waitForRequest((request) => request.url().includes("openid-connect/logout"));
  await page.getByRole("button", { name: "Awa Diallo" }).click();
  await page.getByRole("menuitem", { name: "Se déconnecter" }).click();
  await logout;
});
