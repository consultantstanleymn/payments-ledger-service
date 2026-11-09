# Ops console

The operations console is a read-mostly React 19, TypeScript and Sass single-page app served by the Spring Boot service at `/console/`.

## Pages

| Page | Route | Purpose |
|---|---|---|
| Sign in | `#/signin` | Operator enters the API key. The key is kept in `sessionStorage` only. The admin key never persists. |
| Accounts | `#/accounts` | Lists accounts and balances, shows journal entries for one account. |
| Payments | `#/payments` | Finds payments by status or external reference. |
| Operations | `#/operations` | Shows the latest reconciliation report and redrives the dead-letter topic behind a confirm dialog. |

## Paging contract

The three list endpoints use one keyset paging contract.

Query:

- `limit`: 1 to 100, default 25.
- `cursor`: opaque base64url of JSON `{"k":<sort key>,"id":"<unique id>","s":"<sort name>"}`.

Response:

- `items`: array.
- `nextCursor`: string or null.

Sort always ends in a unique id. A bad, tampered or other-sort cursor returns 400 in the existing error shape from `ApiErrors`.

Endpoints:

- `GET /ops/accounts?currency=&q=` sorts by `(created_at DESC, id DESC)`. `q` is a case-insensitive name prefix, max 64 chars, with `%` and `_` escaped in `LIKE`.
- `GET /ops/accounts/{id}/entries` sorts by `(entry id DESC)` and returns 404 for an unknown account.
- `GET /ops/payments?status=` sorts by `(created_at DESC, id DESC)` and accepts `AUTHORIZED`, `CAPTURED`, `VOIDED`.

The console does all money formatting with integer minor units and `BigInt`. The `ledger` package rule against floating point money stays true. `formatMinor` in `console/src/lib/money.ts` uses `BigInt` and string padding, then `Intl.NumberFormat` only for grouping the major part.

## Security headers

`ConsoleHeadersFilter` adds headers to `/console/**`:

- `Content-Security-Policy`: `default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self' data:; connect-src 'self'; frame-ancestors 'none'; base-uri 'self'; form-action 'self'`
- `X-Content-Type-Options: nosniff`
- `Referrer-Policy: no-referrer`

`/console` static files are open without a key. Every `/ops/*` route needs `X-Api-Key`. The console sends the key the operator types and reads it from `sessionStorage`. It never stores the admin key.

## Build and test

Install:

```bash
PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD=1 npm --prefix console ci
```

Dev:

```bash
npm --prefix console run dev
```

Vite dev runs on 127.0.0.1:26710 and proxies API calls to 127.0.0.1:18080.

Build:

```bash
npm --prefix console run build
```

Vite writes the production build with `base: '/console/'` and `outDir: '../src/main/resources/static/console'`, with `emptyOutDir: true`. That folder is git-ignored. Run this before `./mvnw package` to include the console in the jar.

Check and test:

```bash
(cd console && npm run lint && npx tsc --noEmit && npx vitest run)
(cd console && npx playwright test --grep-invert @screens --reporter=line)
```

The first command runs ESLint, TypeScript checking and 32 Vitest unit tests. The second runs 13 mocked Playwright tests. Six of them are axe checks with tags `wcag2a`, `wcag2aa`, `wcag21a`, `wcag21aa`; all pass with zero violations. A keyboard-only flow test also passes.

## A11y results

All axe checks pass with zero violations for tags `wcag2a`, `wcag2aa`, `wcag21a`, `wcag21aa`. The checked flows are sign-in, accounts, account entries, payments, operations and reduced-motion/theme keyboard navigation. The keyboard-only flow test uses no mouse and reaches every page action.

## Mocked and live

Mocked Playwright tests use `console/e2e/fixtures/*.json` and `console/e2e/mock-api.ts`. They do not start a backend.

The live Playwright test is `console/e2e-live/live.spec.ts`. It runs against the compose stack on 127.0.0.1:18080 and checks:

- the displayed account and entry values from the real `/ops/*` endpoints;
- the zero sum of the capture transaction.

Live evidence: `docs/engagement/evidence/console-live.txt`.

## Related commits

| Area | Commit |
|---|---|
| Ops API tests | e938194 |
| Ops API code | e8d60cb |
| Console skeleton | e30f573 |
| API client and sign-in | ec351a1 |
| Accounts | 6000f87 |
| Payments | 57623db |
| Operations | 439c3c3 |
| Sass and themes | e47b80d |
| Playwright | 2d0cd3c |
| Live e2e | 8531e02 |
| Blackbox | 85d31e2 |
