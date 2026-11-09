import { defineConfig } from '@playwright/test';

const ledgerUrl = (process.env.LEDGER_URL ?? 'http://127.0.0.1:18080').replace(/\/$/, '');
const baseURL = `${ledgerUrl}/console/`;

export default defineConfig({
  testDir: './e2e-live',
  timeout: 60_000,
  fullyParallel: false,
  workers: 1,
  retries: 0,
  reporter: 'line',
  use: {
    baseURL,
    trace: 'retain-on-failure',
    headless: true,
    viewport: { width: 1280, height: 800 }
  },
  projects: [
    {
      name: 'chromium',
      use: { browserName: 'chromium' }
    }
  ]
});
