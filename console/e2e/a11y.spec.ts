import { test, expect } from '@playwright/test';
import type { Page } from '@playwright/test';
import AxeBuilder from '@axe-core/playwright';
import { installMockApi, signIn } from './mock-api';

test.beforeEach(async ({ page }) => {
  await installMockApi(page);
});

async function expectNoWcagViolations(page: Page) {
  const results = await new AxeBuilder({ page })
    .withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa'])
    .analyze();

  expect(results.violations).toEqual([]);
}

test('sign-in page has no WCAG A or AA violations', async ({ page }) => {
  await page.goto('#/signin');
  await expectNoWcagViolations(page);
});

test('accounts page has no WCAG A or AA violations', async ({ page }) => {
  await signIn(page);
  await expectNoWcagViolations(page);
});

test('accounts drawer has no WCAG A or AA violations', async ({ page }) => {
  await signIn(page);
  await page.getByRole('button', { name: 'Alpha Holdings' }).click();
  await expect(page.getByRole('dialog', { name: /Alpha Holdings entries/ })).toBeVisible();
  await expectNoWcagViolations(page);
});

test('payments page has no WCAG A or AA violations', async ({ page }) => {
  await signIn(page);
  await page.getByRole('link', { name: 'Payments' }).click();
  await expect(page.getByRole('button', { name: 'ref-captured' })).toBeVisible();
  await expectNoWcagViolations(page);
});

test('operations page has no WCAG A or AA violations', async ({ page }) => {
  await signIn(page);
  await page.getByRole('link', { name: 'Operations' }).click();
  await expect(page.getByRole('heading', { name: 'Latest reconciliation report' })).toBeVisible();
  await expectNoWcagViolations(page);
});

test('operations redrive dialog has no WCAG A or AA violations', async ({ page }) => {
  await signIn(page);
  await page.getByRole('link', { name: 'Operations' }).click();
  await expect(page.getByRole('heading', { name: 'Latest reconciliation report' })).toBeVisible();

  await page.getByLabel('Admin key').fill('admin-key');
  await page.getByRole('button', { name: 'Redrive' }).click();
  await expect(page.getByRole('dialog', { name: 'Redrive dead-letter topic?' })).toBeVisible();

  await expectNoWcagViolations(page);
});
