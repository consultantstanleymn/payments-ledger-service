// k6 load test. Mixed transfers (70%) and balance reads (30%).
// Gates: error rate below 0.1%, zero balance-invariant violations, and the SLO thresholds on
// transfers (p95 under 300 ms, error rate under 0.5 percent). A breach makes k6 exit with code 99.
import http from 'k6/http';
import { check } from 'k6';
import { Counter } from 'k6/metrics';

const BASE = __ENV.BASE || 'http://127.0.0.1:18080';
const VUS = parseInt(__ENV.VUS || '20');
const DURATION = __ENV.DURATION || '2m';
const ACCOUNTS = 20;
const FUNDING = 100000000; // minor units per account, enough that no transfer is rejected
const HEADERS = { 'X-Api-Key': __ENV.API_KEY || 'dev-key-local-only', 'Content-Type': 'application/json' };

const violations = new Counter('invariant_violations');

export const options = {
  // A slow database (the forced-fail run) makes account setup slow. Give it room.
  setupTimeout: '300s',
  scenarios: {
    mixed: { executor: 'constant-vus', vus: VUS, duration: DURATION },
  },
  thresholds: {
    http_req_failed: ['rate<0.001'],
    'http_req_duration{name:transfer}': [{ threshold: 'p(95)<300', abortOnFail: false }],
    'http_req_failed{name:transfer}': [{ threshold: 'rate<0.005', abortOnFail: false }],
    invariant_violations: ['count==0'],
  },
};

function post(path, key, body, name) {
  return http.post(BASE + path, JSON.stringify(body), {
    headers: Object.assign({ 'Idempotency-Key': key }, HEADERS),
    tags: { name: name || path },
  });
}

export function setup() {
  const run = Date.now();
  const bank = post('/accounts', `bank-${run}`, { name: 'bank', currency: 'USD', floorMinor: -1000000000000 }).json('accountId');
  const ids = [];
  for (let i = 0; i < ACCOUNTS; i++) {
    const id = post('/accounts', `acct-${run}-${i}`, { name: `acct-${i}`, currency: 'USD' }).json('accountId');
    const r = post('/transfers', `fund-${run}-${i}`, { fromAccountId: bank, toAccountId: id, amountMinor: FUNDING });
    if (r.status !== 201) throw new Error('funding failed ' + r.status);
    ids.push(id);
  }
  return { bank, ids };
}

export default function (data) {
  const n = data.ids.length;
  if (Math.random() < 0.7) {
    const a = Math.floor(Math.random() * n);
    const b = (a + 1 + Math.floor(Math.random() * (n - 1))) % n;
    const r = post('/transfers', `t-${__VU}-${__ITER}-${Date.now()}`, {
      fromAccountId: data.ids[a],
      toAccountId: data.ids[b],
      amountMinor: 1 + Math.floor(Math.random() * 100),
    }, 'transfer');
    check(r, { 'transfer 201': (x) => x.status === 201 });
  } else {
    const r = http.get(`${BASE}/accounts/${data.ids[Math.floor(Math.random() * n)]}/balance`, { headers: HEADERS });
    check(r, { 'balance 200': (x) => x.status === 200 });
  }
}

export function teardown(data) {
  // Money is neither created nor destroyed: bank plus all accounts sum to zero, none below floor.
  let sum = 0;
  for (const id of [data.bank].concat(data.ids)) {
    const a = http.get(`${BASE}/accounts/${id}/balance`, { headers: HEADERS }).json();
    sum += a.postedMinor;
    if (a.postedMinor - a.heldMinor < a.floorMinor) violations.add(1);
  }
  if (sum !== 0) violations.add(1);
}
