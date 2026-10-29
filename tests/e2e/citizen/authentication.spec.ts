import { expect, test } from "@playwright/test";
import { mockOidc, mockPublicSite } from "../../fixtures/api";

test.beforeEach(async ({ page }) => {
  await mockOidc(page);
  await mockPublicSite(page);
});

test("accueil public affiche les formalités et la connexion", async ({ page }) => {
  await page.goto("/");
  await expect(page.getByRole("heading", { name: "Vos démarches consulaires, simplifiées" })).toBeVisible();
  await expect(page.getByRole("button", { name: "Commencer une démarche" })).toBeVisible();
  await expect(page.getByRole("banner").getByText("Consulat test")).toBeVisible();
  await expect(page.getByRole("heading", { name: "Mes dossiers" })).toHaveCount(0);
});

test("la connexion redirige vers le fournisseur d’identité", async ({ page }) => {
  await page.goto("/");
  await page.getByRole("button", { name: "Se connecter" }).click();
  await expect(page.getByRole("button", { name: "Continuer sans compte" })).toBeVisible();
  await page.getByRole("button", { name: "Se connecter", exact: true }).click();
  await expect(page).toHaveURL(/openid-connect\/auth/);
});

test("continuer sans compte ouvre l’assistant", async ({ page }) => {
  await page.goto("/");
  await page.getByRole("button", { name: "Se connecter" }).click();
  await page.getByRole("button", { name: "Continuer sans compte" }).click();
  await expect(page.getByRole("heading", { name: "Pour quel pays cette démarche est-elle destinée ?" })).toBeVisible();
});
