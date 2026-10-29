import { expect, test } from "@playwright/test";
import {
  CASE_ID,
  DOCUMENT_ID,
  fulfillJson,
  mockMe,
  mockOidc,
  mockPublicSite,
  ok,
  ORG_ID,
  seedOidcUser,
} from "../../fixtures/api";

const agentCase = {
  id: CASE_ID,
  reference: "KV-2026-AB12CD34",
  status: "IN_PROGRESS",
  organizationId: ORG_ID,
  applicant: { displayName: "Awa Diallo", email: "awa@example.test" },
  procedureDefinitionId: "22222222-2222-2222-2222-222222222222",
  procedureCode: "VISA-GW",
  procedureNameI18n: { fr: "Visa tourisme" },
  procedureVersionId: "55555555-5555-5555-5555-555555555555",
  procedureVersionNumber: 1,
  applicantFacts: { nationality: "FR" },
  eligibilityPassed: true,
  createdAt: "2026-10-20T08:00:00Z",
  nextAction: "UPLOAD_DOCUMENTS",
  estimatedInstructionDays: 10,
};

function document(status: string) {
  return {
    id: DOCUMENT_ID,
    requirementCode: "PASSPORT",
    originalFilename: "passeport.pdf",
    status,
    contentType: "application/pdf",
  };
}

test.beforeEach(async ({ page }) => {
  await mockOidc(page);
  await mockPublicSite(page);
});

async function openAsAgent(page: import("@playwright/test").Page) {
  await seedOidcUser(page, { clientId: "agent-portal", roles: ["AGENT"], name: "Agent Test" });
  await mockMe(page, ["AGENT"]);
  await page.route("**/api/v1/agent/cases**", (route) =>
    fulfillJson(route, { content: [agentCase], totalElements: 1, page: 0, size: 50, totalPages: 1, first: true, last: true }),
  );
  await page.goto("/");
  await expect(page.getByRole("heading", { name: "File des dossiers" })).toBeVisible();
}

test("un agent anonyme reste sur l’écran de connexion", async ({ page }) => {
  await page.goto("/");
  await expect(page.getByText("Connexion réservée aux agents")).toBeVisible();
  await expect(page.getByRole("heading", { name: "File des dossiers" })).toHaveCount(0);
  await page.getByRole("button", { name: "Se connecter" }).click();
  await expect(page).toHaveURL(/openid-connect\/auth/);
});

test("un rôle citoyen est refusé sur le portail agent", async ({ page }) => {
  await seedOidcUser(page, { clientId: "agent-portal", roles: ["CITIZEN"], name: "Awa Diallo" });
  await mockMe(page, ["CITIZEN"]);
  await page.goto("/");
  await expect(page.getByText(/rôle agent/)).toBeVisible();
  await expect(page.getByRole("heading", { name: "File des dossiers" })).toHaveCount(0);
});

test("la file filtre, ouvre un dossier, accepte une pièce et demande une correction", async ({ page }) => {
  let documentStatus = "UPLOADED";
  await openAsAgent(page);
  await expect(page.getByText("KV-1234")).toBeVisible();
  await expect(page.getByText("Awa Diallo")).toBeVisible();

  const filtered = page.waitForRequest((request) => request.url().includes("status=CREATED"));
  await page.getByRole("tablist", { name: "Statut" }).getByRole("button", { name: "Créé" }).click();
  await filtered;

  await page.route(`**/api/v1/cases/${CASE_ID}`, (route) => {
    if (route.request().method() === "GET") {
      return fulfillJson(route, ok(agentCase));
    }
    return route.fallback();
  });
  await page.route(`**/api/v1/cases/${CASE_ID}/documents/${DOCUMENT_ID}/accept`, (route) => {
    documentStatus = "ACCEPTED";
    return fulfillJson(route, ok(document(documentStatus)));
  });
  await page.route(`**/api/v1/cases/${CASE_ID}/documents/${DOCUMENT_ID}/request-correction`, (route) => {
    documentStatus = "CORRECTION_REQUESTED";
    return fulfillJson(route, ok(document(documentStatus)));
  });
  await page.route(`**/api/v1/cases/${CASE_ID}/documents**`, (route) => {
    if (route.request().method() !== "GET") {
      return route.fallback();
    }
    return fulfillJson(route, ok([document(documentStatus)]));
  });
  await page.route(`**/api/v1/cases/${CASE_ID}/orders**`, (route) => fulfillJson(route, ok([])));
  await page.route(`**/api/v1/cases/${CASE_ID}/hash-alerts**`, (route) => fulfillJson(route, ok([])));
  await page.route(`**/api/v1/cases/${CASE_ID}/upload-refusals**`, (route) => fulfillJson(route, ok([])));
  await page.route(`**/api/v1/cases/${CASE_ID}/appointments**`, (route) => fulfillJson(route, ok([])));
  await page.route(`**/api/v1/cases/${CASE_ID}/history**`, (route) => fulfillJson(route, ok([])));

  await page.getByRole("tablist", { name: "Statut" }).getByRole("button", { name: "Tous" }).click();
  await page.getByText("KV-1234").click();
  await expect(page.getByRole("heading", { name: "Pièces" })).toBeVisible();
  await expect(page.getByText("Déposé")).toBeVisible();

  await page.getByRole("button", { name: "Accepter" }).click();
  await expect(page.getByText("Accepté", { exact: true })).toBeVisible();

  await page.getByRole("button", { name: "Demander une correction" }).click();
  await page.getByLabel("Motif (liste)").selectOption("document.correction.unreadable");
  await page.getByRole("button", { name: "Envoyer la demande de correction" }).click();
  await expect(page.getByText("Correction", { exact: true })).toBeVisible();
});

test("la déconnexion agent appelle la fin de session", async ({ page }) => {
  await openAsAgent(page);
  const logout = page.waitForRequest((request) => request.url().includes("openid-connect/logout"));
  await page.getByRole("button", { name: "Se déconnecter" }).click();
  await logout;
});
