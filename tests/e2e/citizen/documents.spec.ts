import { expect, test } from "@playwright/test";
import {
  CASE_ID,
  DOCUMENT_ID,
  fulfillJson,
  mockMe,
  mockOidc,
  mockProcedures,
  mockPublicSite,
  ok,
  PROCEDURE_ID,
  seedOidcUser,
  visaProcedure,
} from "../../fixtures/api";

const caseItem = {
  id: CASE_ID,
  reference: "KV-2026-AB12CD34",
  status: "CREATED",
  procedureDefinitionId: PROCEDURE_ID,
  procedureCode: "VISA-GW",
  procedureNameI18n: { fr: "Visa tourisme" },
  procedureVersionId: "55555555-5555-5555-5555-555555555555",
  procedureVersionNumber: 1,
  applicant: { displayName: "Awa Diallo", email: "awa@example.test" },
  applicantFacts: {},
  eligibilityPassed: true,
  nextAction: "UPLOAD_DOCUMENTS",
  listActionMessageKey: "case.list_action.UPLOAD_DOCUMENTS",
  listSubtitleMessageKey: "case.list_subtitle.UPLOAD_DOCUMENTS",
  listUrgencyGroup: "ACTION_REQUIRED",
  createdAt: "2026-10-20T08:00:00Z",
};

test("un citoyen dépose une pièce sur son dossier", async ({ page }) => {
  await mockOidc(page);
  await mockPublicSite(page);
  await mockProcedures(page);
  await seedOidcUser(page, { clientId: "citizen-portal", roles: ["CITIZEN"], name: "Awa Diallo" });
  await mockMe(page, ["CITIZEN"]);
  let uploaded = false;

  await page.route("**/api/v1/cases**", async (route) => {
    const url = route.request().url();
    if (route.request().method() === "POST" && url.includes("/documents")) {
      uploaded = true;
      await fulfillJson(route, ok({
        id: DOCUMENT_ID,
        requirementCode: "PASSPORT",
        originalFilename: "passeport.pdf",
        status: "UPLOADED",
        contentType: "application/pdf",
      }));
      return;
    }
    if (url.includes("/documents")) {
      await fulfillJson(
        route,
        ok(uploaded
          ? [{
              id: DOCUMENT_ID,
              requirementCode: "PASSPORT",
              originalFilename: "passeport.pdf",
              status: "UPLOADED",
              contentType: "application/pdf",
            }]
          : []),
      );
      return;
    }
    if (url.includes("/orders") || url.includes("/appointments")) {
      await fulfillJson(route, ok([]));
      return;
    }
    if (url.includes(`/cases/${CASE_ID}`)) {
      await fulfillJson(route, ok(caseItem));
      return;
    }
    await fulfillJson(route, {
      content: [caseItem],
      totalElements: 1,
      page: 0,
      size: 50,
      totalPages: 1,
      first: true,
      last: true,
    });
  });
  await page.route("**/api/v1/procedures/**", (route) => fulfillJson(route, ok(visaProcedure())));
  await page.route("**/api/v1/appointment-slots**", (route) => fulfillJson(route, ok([])));

  await page.goto("/");
  await page.getByRole("button", { name: "Déposer" }).click();
  await expect(page.getByText("Passeport")).toBeVisible();
  await page.locator("input[type=file]").setInputFiles({
    name: "passeport.pdf",
    mimeType: "application/pdf",
    buffer: Buffer.from("%PDF-1.4"),
  });
  await expect(page.getByText("Déposé")).toBeVisible();
});
