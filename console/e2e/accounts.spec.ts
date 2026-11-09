import { test, expect } from '@playwright/test';
import { installMockApi, signIn } from './mock-api';

test.beforeEach(async ({ page }) => {
  await installMockApi(page);
});

test('accounts page loads more, filters, and opens an entries drawer', async ({ page }) => {
  await signIn(page);

  await expect(page.getByRole('button', { name: 'Beta Payments' })).toBeVisible();

  await page.getByRole('button', { name: 'Load more' }).click();
  await expect(page.getByRole('button', { name: 'Alpha Operations' })).toBeVisible();
  await expect(page.getByRole('button', { name: 'Load more' })).toBeDisabled();

  await page.getByLabel('Search accounts').fill('Alpha');
  await expect(page.getByRole('button', { name: 'Alpha Operations' })).toBeVisible();
  await expect(page.getByRole('button', { name: 'Beta Payments' })).toHaveCount(0);

  await page.getByRole('button', { name: 'Alpha Holdings' }).click();
  const drawer = page.getByRole('dialog', { name: /Alpha Holdings entries/ });
  await expect(drawer).toBeVisible();
  await expect(drawer.getByText('entry-1', { exact: true })).toBeVisible();
});
