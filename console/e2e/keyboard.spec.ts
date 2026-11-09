import { test, expect } from '@playwright/test';
import { installMockApi } from './mock-api';

test.beforeEach(async ({ page }) => {
  await installMockApi(page);
});

test('keyboard only: sign in, open drawer, close with Escape, open and cancel redrive dialog', async ({
  page
}) => {
  await page.goto('#/signin');

  await page.keyboard.press('Tab');
  await page.keyboard.type('dev-key-local-only');
  await page.keyboard.press('Enter');

  await expect(page.getByRole('button', { name: 'Alpha Holdings' })).toBeVisible();

  await page.keyboard.press('Tab');
  await page.keyboard.press('Tab');
  await page.keyboard.press('Tab');
  await page.keyboard.press('Tab');
  await page.keyboard.press('Tab');
  await page.keyboard.press('Tab');
  await page.keyboard.press('Tab');
  await page.keyboard.press('Enter');

  const drawer = page.getByRole('dialog', { name: /Alpha Holdings entries/ });
  await expect(drawer).toBeVisible();
  await page.keyboard.press('Escape');
  await expect(drawer).toHaveCount(0);

  await page.keyboard.press('Shift+Tab');
  await page.keyboard.press('Shift+Tab');
  await page.keyboard.press('Shift+Tab');
  await page.keyboard.press('Enter');

  await expect(page.getByRole('heading', { name: 'Latest reconciliation report' })).toBeVisible();

  await page.keyboard.press('Tab');
  await page.keyboard.type('admin-key');
  await page.keyboard.press('Tab');
  await page.keyboard.press('Tab');
  await page.keyboard.press('Enter');

  const redriveDialog = page.getByRole('dialog', { name: 'Redrive dead-letter topic?' });
  await expect(redriveDialog).toBeVisible();
  await page.keyboard.press('Tab');
  await page.keyboard.press('Enter');
  await expect(redriveDialog).toHaveCount(0);
});
