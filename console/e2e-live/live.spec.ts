import { test, expect } from '@playwright/test';
import type { APIRequestContext, Page } from '@playwright/test';
import AxeBuilder from '@axe-core/playwright';

const API_KEY = 'dev-key-local-only';
const API_URL = (process.env.LEDGER_URL ?? 'http://127.0.0.1:18080').replace(/\/$/, '');
const CURRENCY = 'USD';

interface AccountCreationResponse {
  accountId: string;
}

interface PaymentCreationResponse {
  id: string;
}

interface EntryItem {
  entryId: string;
  txnId: string;
  kind: string;
  reference: string;
  amountMinor: number;
  createdAt: string;
}

interface EntriesPage {
  items: EntryItem[];
  nextCursor: string | null;
}

function idempotencyKey(label: string): string {
  return `${label}-${Date.now()}-${Math.random().toString(16).slice(2)}`;
}

async function postJson(
  request: APIRequestContext,
  path: string,
  body: Record<string, unknown>,
  label: string
): Promise<Record<string, unknown>> {
  const response = await request.post(`${API_URL}${path}`, {
    headers: {
      'X-Api-Key': API_KEY,
      'Content-Type': 'application/json',
      'Idempotency-Key': idempotencyKey(label)
    },
    data: body
  });

  if (!response.ok()) {
    throw new Error(`${path} returned ${response.status()}: ${await response.text()}`);
  }

  return (await response.json()) as Record<string, unknown>;
}

async function createAccount(
  request: APIRequestContext,
  name: string,
  floorMinor?: number
): Promise<string> {
  const body: Record<string, unknown> = { name, currency: CURRENCY };
  if (floorMinor !== undefined) {
    body.floorMinor = floorMinor;
  }

  const json = await postJson(request, '/accounts', body, `account-${name}`);
  return (json as AccountCreationResponse).accountId;
}

async function getEntries(request: APIRequestContext, accountId: string): Promise<EntryItem[]> {
  const response = await request.get(`${API_URL}/ops/accounts/${accountId}/entries?limit=100`, {
    headers: { 'X-Api-Key': API_KEY }
  });

  if (!response.ok()) {
    throw new Error(`GET entries returned ${response.status()}: ${await response.text()}`);
  }

  const body = (await response.json()) as EntriesPage;
  return body.items;
}

function displayMinor(minor: number | bigint, currency: string): string {
  const value = BigInt(minor);
  const negative = value < 0n;
  const unsigned = (negative ? -value : value).toString();
  const major = unsigned.length > 2 ? unsigned.slice(0, -2) : '0';
  const fraction = unsigned.length > 2 ? unsigned.slice(-2) : unsigned.padStart(2, '0');
  const groupedMajor = major.replace(/\B(?=(\d{3})+(?!\d))/g, ',');
  return `${negative ? '-' : ''}${groupedMajor}.${fraction} ${currency}`;
}

function parseDisplayedMinor(text: string): bigint {
  const normalized = text.replace(/,/g, '').replace(/\s+/g, ' ').trim();
  const match = normalized.match(/^(-?)(0|[1-9]\d*)(?:\.(\d{1,2}))?\s+USD$/);

  if (!match) {
    throw new Error(`Could not parse displayed amount: ${text}`);
  }

  const sign = match[1] === '-' ? -1n : 1n;
  const whole = BigInt(match[2]);
  const fraction = (match[3] ?? '').padEnd(2, '0').slice(0, 2) || '00';
  return sign * (whole * 100n + BigInt(fraction));
}

async function signIn(page: Page) {
  await page.goto('#/signin');
  await page.getByLabel('API key').fill(API_KEY);
  await page.getByRole('button', { name: 'Sign in' }).click();
  await expect(page.getByLabel('Search accounts')).toBeVisible();
}

async function expectNoWcagViolations(page: Page) {
  const results = await new AxeBuilder({ page })
    .withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa'])
    .analyze();

  expect(results.violations).toEqual([]);
}

test('live console flow', async ({ page, request }) => {
  const timestamp = Date.now();
  const prefix = `live-${timestamp}`;
  const externalRef = prefix;
  const payerName = `${prefix}-payer`;
  const payeeName = `${prefix}-payee`;
  const bankName = `${prefix}-bank`;

  const fundMinor = 10000;
  const authorizeMinor = 4000;
  const captureMinor = 3500;

  const bankId = await createAccount(request, bankName, -100000000);
  const payerId = await createAccount(request, payerName);
  const payeeId = await createAccount(request, payeeName);

  await postJson(
    request,
    '/transfers',
    { fromAccountId: bankId, toAccountId: payerId, amountMinor: fundMinor },
    `fund-${prefix}`
  );

  const paymentJson = await postJson(
    request,
    '/payments',
    {
      payerAccountId: payerId,
      payeeAccountId: payeeId,
      amountMinor: authorizeMinor,
      externalRef
    },
    `payment-${prefix}`
  );
  const paymentId = (paymentJson as PaymentCreationResponse).id;

  await postJson(
    request,
    `/payments/${paymentId}/capture`,
    { amountMinor: captureMinor },
    `capture-${prefix}`
  );

  await signIn(page);

  await page.getByLabel('Search accounts').fill(prefix);
  await expect(page.getByRole('button', { name: payerName })).toBeVisible();
  await expect(page.getByRole('button', { name: payeeName })).toBeVisible();

  await expectNoWcagViolations(page);

  await page.getByRole('button', { name: payerName }).click();
  const drawer = page.getByRole('dialog', { name: `${payerName} entries` });
  await expect(drawer).toBeVisible();

  const table = drawer.getByRole('table');
  await expect(table).toBeVisible();
  await expect(table.locator('tbody tr').first()).toBeVisible();

  const rows = table.locator('tbody tr');
  const rowCount = await rows.count();
  const payerAmounts: bigint[] = [];

  for (let index = 0; index < rowCount; index += 1) {
    const amountText = await rows
      .nth(index)
      .locator('td')
      .filter({ hasText: CURRENCY })
      .innerText();
    payerAmounts.push(parseDisplayedMinor(amountText));
  }

  const payerNet = payerAmounts.reduce((total, amount) => total + amount, 0n);
  expect(payerNet.toString()).toBe((fundMinor - captureMinor).toString());

  await drawer.getByRole('button', { name: 'Close' }).click();
  await expect(drawer).not.toBeVisible();

  const payerEntries = await getEntries(request, payerId);
  const payeeEntries = await getEntries(request, payeeId);

  const captureEntries = [...payerEntries, ...payeeEntries].filter(
    (entry) => entry.kind === 'CAPTURE' && entry.reference === paymentId
  );

  expect(captureEntries.length).toBeGreaterThan(0);
  const captureSum = captureEntries.reduce((total, entry) => total + BigInt(entry.amountMinor), 0n);
  expect(captureSum.toString()).toBe('0');

  await page.getByRole('link', { name: 'Payments' }).click();
  await expect(page.getByLabel('External reference')).toBeVisible();

  await page.getByLabel('External reference').fill(externalRef);
  await page.getByRole('button', { name: 'Look up' }).click();

  const detailPanel = page.getByRole('complementary', { name: `Payment ${externalRef} details` });
  await expect(detailPanel).toBeVisible();
  await expect(detailPanel).toContainText(`Captured${displayMinor(captureMinor, CURRENCY).replace(` ${CURRENCY}`, '')}`);
});
