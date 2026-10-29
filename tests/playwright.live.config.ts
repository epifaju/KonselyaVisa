import { defineConfig, devices } from "@playwright/test";
import path from "node:path";
import { fileURLToPath } from "node:url";

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");

/**
 * Real stack smoke. Not part of the default `npx playwright test` run.
 * Requires the API on http://127.0.0.1:18083 (docker compose backend).
 */
export default defineConfig({
  testDir: "./e2e/live",
  timeout: 60_000,
  expect: { timeout: 15_000 },
  forbidOnly: true,
  retries: 0,
  reporter: [["list"], ["html", { open: "never", outputFolder: path.join(root, "playwright-live-report") }]],
  outputDir: path.join(root, "test-results"),
  use: {
    ...devices["Desktop Chrome"],
    baseURL: "http://127.0.0.1:5175",
    trace: "retain-on-failure",
    screenshot: "only-on-failure",
    video: "retain-on-failure",
  },
  webServer: [
    {
      command: "npm run dev -- --host 127.0.0.1 --port 5175",
      cwd: path.join(root, "frontend/citizen-portal"),
      url: "http://127.0.0.1:5175",
      reuseExistingServer: false,
      timeout: 120_000,
    },
    {
      command: "npm run dev -- --host 127.0.0.1 --port 5176",
      cwd: path.join(root, "frontend/admin-portal"),
      url: "http://127.0.0.1:5176",
      reuseExistingServer: false,
      timeout: 120_000,
    },
  ],
});
