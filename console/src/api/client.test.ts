import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { apiGet, apiPost } from './client';
import { getApiKey, setApiKey } from './session';

const fetchMock = vi.fn();

function jsonResponse(status: number, body: unknown): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' }
  });
}

describe('api client', () => {
  beforeEach(() => {
    vi.stubGlobal('fetch', fetchMock);
    sessionStorage.clear();
    fetchMock.mockReset();
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('adds the API key and serializes params for GET requests', async () => {
    setApiKey('test-key');
    fetchMock.mockResolvedValue(jsonResponse(200, { items: [], nextCursor: null }));

    await apiGet<{ items: never[]; nextCursor: null }>('/ops/accounts', {
      limit: 25,
      q: undefined
    });

    expect(fetchMock).toHaveBeenCalledTimes(1);
    const [url, init] = fetchMock.mock.calls[0] as [string, RequestInit];
    expect(url).toBe('/ops/accounts?limit=25');
    const requestHeaders = init.headers as Headers;
    expect(requestHeaders.get('X-Api-Key')).toBe('test-key');
  });

  it('clears the key and emits an event on 401', async () => {
    setApiKey('bad-key');
    const unauthorizedHandler = vi.fn();
    window.addEventListener('ledger.console.apiUnauthorized', unauthorizedHandler);
    fetchMock.mockResolvedValue(jsonResponse(401, { code: 'unauthorized', message: 'Invalid key' }));

    await expect(apiGet('/ops/accounts', { limit: 1 })).rejects.toMatchObject({
      status: 401,
      code: 'unauthorized',
      message: 'Invalid key'
    });

    expect(getApiKey()).toBeNull();
    expect(unauthorizedHandler).toHaveBeenCalledTimes(1);
    window.removeEventListener('ledger.console.apiUnauthorized', unauthorizedHandler);
  });

  it('maps error JSON to ApiError for failed POST requests', async () => {
    fetchMock.mockResolvedValue(jsonResponse(422, { code: 'validation_failed', message: 'Bad input' }));

    await expect(apiPost('/admin/dead-letter/redrive', { id: 'x' })).rejects.toMatchObject({
      status: 422,
      code: 'validation_failed',
      message: 'Bad input'
    });
  });

  it('returns undefined for 204 responses', async () => {
    fetchMock.mockResolvedValue(new Response(null, { status: 204 }));

    await expect(apiPost('/admin/dead-letter/redrive', { id: 'x' })).resolves.toBeUndefined();
  });
});
