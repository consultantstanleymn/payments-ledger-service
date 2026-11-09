import type { Page, Route } from '@playwright/test';
import { expect } from '@playwright/test';
import accountsPage1 from './fixtures/accounts-page-1.json' with { type: 'json' };
import accountsPage2 from './fixtures/accounts-page-2.json' with { type: 'json' };
import accountsAll from './fixtures/accounts-all.json' with { type: 'json' };
import paymentsPage from './fixtures/payments-page-1.json' with { type: 'json' };
import paymentLookup from './fixtures/payments-lookup.json' with { type: 'json' };
import entriesPage from './fixtures/entries.json' with { type: 'json' };
import reconciliation from './fixtures/reconciliation.json' with { type: 'json' };
import redriveResult from './fixtures/redrive.json' with { type: 'json' };

interface AccountFixture {
  id: string;
  name: string;
  currency: string;
  postedMinor: number;
  heldMinor: number;
  availableMinor: number;
  floorMinor: number;
  createdAt: string;
}

interface PaymentFixture {
  id: string;
  externalRef: string;
  status: string;
  currency: string;
  amountMinor: number;
  authorizedMinor: number;
  capturedMinor: number;
  refundedMinor: number;
  createdAt: string;
}

export interface RedriveRequest {
  max: string;
  adminKey: string;
}

export interface MockApi {
  redriveRequests: RedriveRequest[];
}

const allAccounts = accountsAll as AccountFixture[];
const allPayments = paymentsPage.items as PaymentFixture[];

function fulfillJson(route: Route, status: number, body: unknown) {
  return route.fulfill({
    status,
    contentType: 'application/json',
    body: JSON.stringify(body)
  });
}

function apiKeyIsValid(route: Route): boolean {
  return route.request().headers()['x-api-key'] === 'dev-key-local-only';
}

function invalidApiKeyBody() {
  return { code: 'UNAUTHORIZED', message: 'Invalid API key' };
}

async function handleOpsAccounts(route: Route) {
  const url = new URL(route.request().url());
  const path = url.pathname;

  if (path === '/ops/accounts') {
    if (!apiKeyIsValid(route)) {
      return fulfillJson(route, 401, invalidApiKeyBody());
    }

    const cursor = url.searchParams.get('cursor');
    const query = url.searchParams.get('q') ?? '';
    const currency = url.searchParams.get('currency') ?? '';

    if (query === 'server-error') {
      return fulfillJson(route, 500, { code: 'INTERNAL', message: 'Mock server error' });
    }

    if (cursor === 'page-2') {
      return fulfillJson(route, 200, accountsPage2);
    }

    if (currency || query) {
      const items = allAccounts.filter((account) => {
        const matchesCurrency = !currency || account.currency === currency;
        const matchesQuery =
          !query || account.name.toLowerCase().startsWith(query.toLowerCase());
        return matchesCurrency && matchesQuery;
      });

      return fulfillJson(route, 200, { items, nextCursor: null });
    }

    return fulfillJson(route, 200, accountsPage1);
  }

  const entriesMatch = path.match(/^\/ops\/accounts\/([^/]+)\/entries$/);
  if (entriesMatch) {
    if (!apiKeyIsValid(route)) {
      return fulfillJson(route, 401, invalidApiKeyBody());
    }

    if (entriesMatch[1] === 'missing-account') {
      return fulfillJson(route, 404, { code: 'NOT_FOUND', message: 'Account not found' });
    }

    return fulfillJson(route, 200, entriesPage);
  }

  return route.fallback();
}

async function handleOpsPayments(route: Route) {
  const url = new URL(route.request().url());
  if (url.pathname !== '/ops/payments') {
    return route.fallback();
  }

  if (!apiKeyIsValid(route)) {
    return fulfillJson(route, 401, invalidApiKeyBody());
  }

  const status = url.searchParams.get('status') ?? '';
  if (status) {
    const items = allPayments.filter((payment) => payment.status === status);
    return fulfillJson(route, 200, { items, nextCursor: null });
  }

  return fulfillJson(route, 200, paymentsPage);
}

async function handlePaymentsLookup(route: Route) {
  const url = new URL(route.request().url());
  if (url.pathname !== '/payments') {
    return route.fallback();
  }

  const reference = url.searchParams.get('externalRef') ?? '';
  if (reference === 'not-found') {
    return fulfillJson(route, 404, { code: 'NOT_FOUND', message: 'Payment not found' });
  }

  return fulfillJson(route, 200, paymentLookup);
}

async function handleLatestReconciliation(route: Route) {
  const url = new URL(route.request().url());
  if (url.pathname !== '/reconciliations/latest') {
    return route.fallback();
  }

  return fulfillJson(route, 200, reconciliation);
}

async function handleRedrive(route: Route, redriveRequests: RedriveRequest[]) {
  const url = new URL(route.request().url());
  if (url.pathname !== '/admin/dlt/redrive') {
    return route.fallback();
  }

  if (route.request().method() !== 'POST') {
    return route.fallback();
  }

  redriveRequests.push({
    max: url.searchParams.get('max') ?? '',
    adminKey: route.request().headers()['x-admin-key'] ?? ''
  });

  return fulfillJson(route, 200, redriveResult);
}

export async function installMockApi(page: Page): Promise<MockApi> {
  const redriveRequests: RedriveRequest[] = [];

  await page.route('**/ops/accounts**', (route) => handleOpsAccounts(route));
  await page.route('**/ops/payments**', (route) => handleOpsPayments(route));
  await page.route('**/reconciliations/latest**', (route) => handleLatestReconciliation(route));
  await page.route('**/admin/dlt/redrive**', (route) => handleRedrive(route, redriveRequests));
  await page.route('**/payments**', (route) => {
    const path = new URL(route.request().url()).pathname;
    if (path !== '/payments') {
      return route.fallback();
    }

    return handlePaymentsLookup(route);
  });

  return { redriveRequests };
}

export async function signIn(page: Page) {
  await page.goto('#/signin');
  await page.getByLabel('API key').fill('dev-key-local-only');
  await page.getByRole('button', { name: 'Sign in' }).click();
  await expect(page.getByRole('button', { name: 'Alpha Holdings' })).toBeVisible();
}
