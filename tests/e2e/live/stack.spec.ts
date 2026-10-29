import { expect, test } from "@playwright/test";

test("l’API répond et l’accueil citoyen s’affiche", async ({ page, request }) => {
  const health = await request.get("http://127.0.0.1:18083/actuator/health");
  expect(health.ok()).toBeTruthy();

  await page.goto("/");
  await expect(page.getByRole("heading", { name: "Vos démarches consulaires, simplifiées" })).toBeVisible();
});

test("le portail agent affiche l’écran de connexion", async ({ page }) => {
  await page.goto("http://127.0.0.1:5176/");
  await expect(page.getByRole("button", { name: "Se connecter" })).toBeVisible();
});
