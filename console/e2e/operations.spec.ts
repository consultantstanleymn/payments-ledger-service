import { test, expect } from '@playwright/test';
import { installMockApi, signIn, type MockApi } from './mock-api';

let mockApi: MockApi;

test.beforeEach(async ({ page }) => {
  mockApi = await installMockApi(page);
  await signIn(page);
  await page.getByRole('link', { name: 'Operations' }).click();
  await expect(page.getByRole('heading', { name: 'Latest reconciliation report' })).toBeVisible();
});

test('redrive dialog can be cancelled', async ({ page }) => {
  await page.getByLabel('Admin key').fill('admin-key');
  await page.getByRole('button', { name: 'Redrive' }).click();

  const dialog = page.getByRole('dialog', { name: 'Redrive dead-letter topic?' });
  await expect(dialog).toBeVisible();
  await dialog.getByRole('button', { name: 'Cancel' }).click();
  await expect(dialog).toHaveCount(0);

  expect(mockApi.redriveRequests).toHaveLength(0);
});

test('redrive dialog can be confirmed', async ({ page }) => {
  await page.getByLabel('Admin key').fill('admin-key');
  await page.getByRole('button', { name: 'Redrive' }).click();

  const dialog = page.getByRole('dialog', { name: 'Redrive dead-letter topic?' });
  await expect(dialog).toBeVisible();
  await dialog.getByRole('button', { name: 'Confirm redrive' }).click();

  await expect(page.getByText('Read 3, republished 2.')).toBeVisible();

  expect(mockApi.redriveRequests).toHaveLength(1);
  expect(mockApi.redriveRequests[0]).toEqual({ max: '10', adminKey: 'admin-key' });
});
