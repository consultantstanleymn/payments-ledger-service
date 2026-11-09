import { test, expect } from '@playwright/test';
import { installMockApi, signIn } from './mock-api';

test.beforeEach(async ({ page }) => {
  await installMockApi(page);
});

test('payments page filters by status and looks up an external reference', async ({ page }) => {
  await signIn(page);
  await page.getByRole('link', { name: 'Payments' }).click();

  await expect(page.getByRole('button', { name: 'ref-captured' })).toBeVisible();

  await page.getByRole('radio', { name: 'Captured' }).check();
  await expect(page.getByRole('button', { name: 'ref-captured' })).toBeVisible();
  await expect(page.getByRole('button', { name: 'ref-authorized' })).toHaveCount(0);

  await page.getByLabel('External reference').fill('ref-authorized');
  await page.getByRole('button', { name: 'Look up' }).click();
  await expect(
    page.getByRole('heading', { name: 'Payment ref-authorized details' })
  ).toBeVisible();
});
