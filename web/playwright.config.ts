import { defineConfig, devices } from '@playwright/test';

// Runs against the docker compose stack (web on :3300) in demo mode. Override with BASE_URL.
// PW_CHANNEL=chrome uses the locally installed Google Chrome instead of downloading Chromium.
export default defineConfig({
  testDir: './e2e',
  timeout: 60_000,
  expect: { timeout: 15_000 },
  retries: process.env['CI'] ? 1 : 0,
  workers: 1,
  reporter: process.env['CI'] ? [['github'], ['html', { open: 'never' }]] : 'list',
  use: {
    baseURL: process.env['BASE_URL'] ?? 'http://localhost:3300',
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
  },
  projects: [
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'], viewport: { width: 1440, height: 900 }, channel: process.env['PW_CHANNEL'] || undefined },
    },
  ],
});
