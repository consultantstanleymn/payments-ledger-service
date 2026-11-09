import { test, expect } from '@playwright/test';
import { installMockApi } from './mock-api';

test.beforeEach(async ({ page }) => {
  await installMockApi(page);
});

test('bad API key shows an alert', async ({ page }) => {
  await page.goto('#/signin');
  await page.getByLabel('API key').fill('bad-key');
  await page.getByRole('button', { name: 'Sign in' }).click();

  await expect(page.getByRole('alert')).toHaveText('Invalid API key.');
});

test('valid API key lands on the accounts page', async ({ page }) => {
  await page.goto('#/signin');
  await page.getByLabel('API key').fill('dev-key-local-only');
  await page.getByRole('button', { name: 'Sign in' }).click();

  await expect(page.getByRole('button', { name: 'Alpha Holdings' })).toBeVisible();
});
