import { expect, test } from "@playwright/test";
import { fulfillJson, mockMe, mockOidc, mockPublicSite, ok, seedOidcUser } from "../../fixtures/api";

test("un agent crée un créneau", async ({ page }) => {
  await mockOidc(page);
  await mockPublicSite(page);
  await seedOidcUser(page, { clientId: "agent-portal", roles: ["AGENT"], name: "Agent Test" });
  await mockMe(page, ["AGENT"]);
  await page.route("**/api/v1/agent/cases**", (route) =>
    fulfillJson(route, { content: [], totalElements: 0, page: 0, size: 50, totalPages: 0, first: true, last: true }),
  );
  const slots: unknown[] = [];
  await page.route("**/api/v1/appointment-slots**", async (route) => {
    if (route.request().method() === "POST" && !route.request().url().includes("/cancel")) {
      const body = route.request().postDataJSON() as { startsAt: string; endsAt: string; capacity: number };
      slots.push({
        id: "66666666-6666-6666-6666-666666666666",
        startsAt: body.startsAt,
        endsAt: body.endsAt,
        capacity: body.capacity,
        remainingCapacity: body.capacity,
        status: "OPEN",
        locationI18n: { fr: "Consulat — guichet visas" },
      });
      await fulfillJson(route, ok(slots[0]));
      return;
    }
    await fulfillJson(route, ok(slots));
  });

  await page.goto("/");
  await page.getByRole("button", { name: "Créneaux" }).click();
  await expect(page.getByRole("heading", { name: "Créneaux" })).toBeVisible();
  await page.getByLabel("Début").fill("2026-11-02T09:00");
  await page.getByLabel("Fin").fill("2026-11-02T09:30");
  await page.getByRole("button", { name: "Créer le créneau" }).click();
  await expect(page.getByText("2 / 2 places")).toBeVisible();
});
