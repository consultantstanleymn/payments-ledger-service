import { test, expect } from '@playwright/test';
import { installMockApi, signIn } from './mock-api';

function screensOnly() {
  test.skip(process.env.SCREENS !== '1', 'Set SCREENS=1 to write screenshots');
}

test('@screens signin light', async ({ page }) => {
  screensOnly();
  await page.emulateMedia({ colorScheme: 'light' });
  await page.goto('#/signin');
  await page.waitForLoadState('networkidle');
  await page.screenshot({ path: '../docs/img/console-signin-light.png', fullPage: true });
});

test('@screens signin dark', async ({ page }) => {
  screensOnly();
  await page.emulateMedia({ colorScheme: 'dark' });
  await page.goto('#/signin');
  await page.waitForLoadState('networkidle');
  await page.screenshot({ path: '../docs/img/console-signin-dark.png', fullPage: true });
});

test('@screens accounts light', async ({ page }) => {
  screensOnly();
  await installMockApi(page);
  await page.emulateMedia({ colorScheme: 'light' });
  await signIn(page);
  await page.screenshot({ path: '../docs/img/console-accounts-light.png', fullPage: true });
});

test('@screens accounts dark', async ({ page }) => {
  screensOnly();
  await installMockApi(page);
  await page.emulateMedia({ colorScheme: 'dark' });
  await signIn(page);
  await page.screenshot({ path: '../docs/img/console-accounts-dark.png', fullPage: true });
});

test('@screens payments light', async ({ page }) => {
  screensOnly();
  await installMockApi(page);
  await page.emulateMedia({ colorScheme: 'light' });
  await signIn(page);
  await page.getByRole('link', { name: 'Payments' }).click();
  await expect(page.getByRole('table', { name: 'Payments' })).toBeVisible();
  await page.screenshot({ path: '../docs/img/console-payments-light.png', fullPage: true });
});

test('@screens payments dark', async ({ page }) => {
  screensOnly();
  await installMockApi(page);
  await page.emulateMedia({ colorScheme: 'dark' });
  await signIn(page);
  await page.getByRole('link', { name: 'Payments' }).click();
  await expect(page.getByRole('table', { name: 'Payments' })).toBeVisible();
  await page.screenshot({ path: '../docs/img/console-payments-dark.png', fullPage: true });
});

test('@screens operations light', async ({ page }) => {
  screensOnly();
  await installMockApi(page);
  await page.emulateMedia({ colorScheme: 'light' });
  await signIn(page);
  await page.getByRole('link', { name: 'Operations' }).click();
  await expect(page.getByRole('heading', { name: 'Latest reconciliation report' })).toBeVisible();
  await page.screenshot({ path: '../docs/img/console-operations-light.png', fullPage: true });
});

test('@screens operations dark', async ({ page }) => {
  screensOnly();
  await installMockApi(page);
  await page.emulateMedia({ colorScheme: 'dark' });
  await signIn(page);
  await page.getByRole('link', { name: 'Operations' }).click();
  await expect(page.getByRole('heading', { name: 'Latest reconciliation report' })).toBeVisible();
  await page.screenshot({ path: '../docs/img/console-operations-dark.png', fullPage: true });
});
