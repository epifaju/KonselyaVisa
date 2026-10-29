import { defineConfig, devices } from "@playwright/test";
import path from "node:path";
import { fileURLToPath } from "node:url";

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");

export default defineConfig({
  timeout: 30_000,
  expect: { timeout: 10_000 },
  fullyParallel: true,
  forbidOnly: Boolean(process.env.CI),
  retries: process.env.CI ? 1 : 0,
  reporter: [["list"], ["html", { open: "never", outputFolder: path.join(root, "playwright-report") }]],
  outputDir: path.join(root, "test-results"),
  use: {
    trace: "retain-on-failure",
    screenshot: "only-on-failure",
    video: "retain-on-failure",
  },
  projects: [
    {
      name: "citizen",
      testDir: "./e2e/citizen",
      use: { ...devices["Desktop Chrome"], baseURL: "http://127.0.0.1:5175" },
    },
    {
      name: "admin",
      testDir: "./e2e/admin",
      use: { ...devices["Desktop Chrome"], baseURL: "http://127.0.0.1:5176" },
    },
  ],
  webServer: [
    {
      command: "npm run dev -- --host 127.0.0.1 --port 5175",
      cwd: path.join(root, "frontend/citizen-portal"),
      url: "http://127.0.0.1:5175",
      reuseExistingServer: !process.env.CI,
      timeout: 120_000,
    },
    {
      command: "npm run dev -- --host 127.0.0.1 --port 5176",
      cwd: path.join(root, "frontend/admin-portal"),
      url: "http://127.0.0.1:5176",
      reuseExistingServer: !process.env.CI,
      timeout: 120_000,
    },
  ],
});
