import { expect, test } from "@playwright/test";
import { fulfillJson, mockOidc, mockProcedures, mockPublicSite } from "../../fixtures/api";

test.beforeEach(async ({ page }) => {
  await mockOidc(page);
  await mockPublicSite(page);
  await mockProcedures(page);
});

async function reachPassportQuestion(page: import("@playwright/test").Page) {
  await page.goto("/");
  await page.getByRole("button", { name: "Commencer une démarche" }).click();
  await page.getByRole("radio", { name: "Guinée-Bissau" }).click();
  await page.getByRole("button", { name: "Continuer" }).click();
  await page.getByRole("radio", { name: /Visa tourisme/ }).click();
  await page.getByRole("button", { name: "Continuer" }).click();
  await page.getByRole("radio", { name: "France" }).click();
  await page.getByRole("button", { name: "Continuer" }).click();
}

test("un passeport trop court est refusé avant la création du dossier", async ({ page }) => {
  await reachPassportQuestion(page);
  await page.getByRole("radio", { name: "Non, moins de 6 mois" }).click();
  await expect(page.getByText("Sans 6 mois de validité")).toBeVisible();
  await page.getByRole("button", { name: "Continuer" }).click();
  await expect(page.getByRole("alert")).toContainText("cette démarche n’est pas possible");
  await expect(page.getByRole("button", { name: "Créer un compte pour ouvrir le dossier" })).toBeDisabled();
});

test("une éligibilité refusée par l’API reste sur le récapitulatif", async ({ page }) => {
  await page.route("**/api/v1/public/eligibility-tickets", (route) =>
    fulfillJson(route, { success: true, data: { eligible: false } }),
  );
  await reachPassportQuestion(page);
  await page.getByRole("radio", { name: "Oui, plus de 6 mois" }).click();
  await page.getByRole("button", { name: "Continuer" }).click();
  await page.getByRole("checkbox").check();
  await page.getByRole("button", { name: "Créer un compte pour ouvrir le dossier" }).click();
  await expect(page.getByRole("alert")).toContainText("n’est pas éligible");
  await expect(page.getByRole("heading", { name: "Créez votre compte" })).toHaveCount(0);
});

test("un compte invité refuse deux mots de passe différents", async ({ page }) => {
  await page.route("**/api/v1/public/eligibility-tickets", (route) =>
    fulfillJson(route, {
      success: true,
      data: { eligible: true, ticketId: "66666666-6666-6666-6666-666666666666" },
    }),
  );
  await reachPassportQuestion(page);
  await page.getByRole("radio", { name: "Oui, plus de 6 mois" }).click();
  await page.getByRole("button", { name: "Continuer" }).click();
  await page.getByRole("checkbox").check();
  await page.getByRole("button", { name: "Créer un compte pour ouvrir le dossier" }).click();
  await expect(page.getByRole("heading", { name: "Créez votre compte" })).toBeVisible();

  await page.getByRole("textbox", { name: "Adresse e-mail" }).fill("ada@example.test");
  await page.getByRole("textbox", { name: /^Mot de passe/ }).fill("PhraseLongue12");
  await page.getByRole("textbox", { name: "Confirmer le mot de passe" }).fill("AutrePhrase12");
  await page.getByRole("checkbox").check();
  await page.getByRole("button", { name: "Créer le compte et se connecter" }).click();
  await expect(page.getByText("Les deux mots de passe doivent être identiques.")).toBeVisible();
});
