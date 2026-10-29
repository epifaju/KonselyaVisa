import { expect, test } from "@playwright/test";
import { CASE_ID, fulfillJson, mockMe, mockOidc, mockPublicSite, ok, seedOidcUser } from "../../fixtures/api";

test("un collaborateur voit les dossiers de son entreprise", async ({ page }) => {
  await mockOidc(page);
  await mockPublicSite(page);
  await seedOidcUser(page, { clientId: "citizen-portal", roles: ["COMPANY_USER"], name: "Nadia Sow" });
  await mockMe(page, ["COMPANY_USER"]);
  await page.route("**/api/v1/cases**", (route) =>
    fulfillJson(route, {
      content: [{
        id: CASE_ID,
        reference: "KV-2026-AB12CD34",
        status: "CREATED",
        procedureDefinitionId: "22222222-2222-2222-2222-222222222222",
        procedureCode: "VISA-GW",
        procedureNameI18n: { fr: "Visa tourisme" },
        procedureVersionId: "55555555-5555-5555-5555-555555555555",
        procedureVersionNumber: 1,
        applicant: { displayName: "Awa Diallo", email: "awa@example.test" },
        applicantFacts: {},
        eligibilityPassed: true,
        createdByLabel: "Nadia Sow",
        createdByRole: "COMPANY_USER",
        createdAt: "2026-10-20T08:00:00Z",
      }],
      totalElements: 1,
      page: 0,
      size: 100,
      totalPages: 1,
      first: true,
      last: true,
    }),
  );
  await page.route("**/api/v1/company/cases/summary**", (route) =>
    fulfillJson(route, ok({ total: 1, byStatus: { CREATED: 1 } })),
  );

  await page.goto("/");
  await expect(page.getByRole("heading", { name: "Dossiers entreprise" })).toBeVisible();
  await expect(page.getByText("Awa Diallo")).toBeVisible();
  await expect(page.getByText("Collaborateur")).toBeVisible();
});
