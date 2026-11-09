import { defineConfig } from '@playwright/test';

export default defineConfig({
  testDir: './e2e',
  timeout: 30_000,
  fullyParallel: false,
  workers: 2,
  retries: 0,
  reporter: 'line',
  use: {
    baseURL: 'http://127.0.0.1:26711/console/',
    trace: 'retain-on-failure',
    headless: true,
    viewport: { width: 1280, height: 800 }
  },
  webServer: {
    command: 'npm run build && npx vite preview --host 127.0.0.1 --port 26711 --strictPort',
    url: 'http://127.0.0.1:26711/console/',
    reuseExistingServer: false,
    timeout: 120_000
  },
  projects: [
    {
      name: 'chromium',
      use: { browserName: 'chromium' }
    }
  ]
});
