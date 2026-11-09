import { clearApiKey, emitUnauthorized, getApiKey } from './session';

export class ApiError extends Error {
  constructor(
    public readonly status: number,
    public readonly code: string,
    message: string
  ) {
    super(message);
    this.name = 'ApiError';
  }
}

type QueryValue = string | number | undefined;

function buildUrl(path: string, params?: Record<string, QueryValue>): string {
  if (!params) {
    return path;
  }

  const search = new URLSearchParams();
  for (const [key, value] of Object.entries(params)) {
    if (value !== undefined && value !== null && value !== '') {
      search.set(key, String(value));
    }
  }

  const query = search.toString();
  if (!query) {
    return path;
  }

  return `${path}${path.includes('?') ? '&' : '?'}${query}`;
}

function requestHeaders(includeContentType: boolean): Headers {
  const headers = new Headers({ Accept: 'application/json' });
  const apiKey = getApiKey();
  if (apiKey) {
    headers.set('X-Api-Key', apiKey);
  }
  if (includeContentType) {
    headers.set('Content-Type', 'application/json');
  }
  return headers;
}

async function errorFrom(response: Response): Promise<ApiError> {
  let code = 'request_failed';
  let message = `Request failed with status ${response.status}`;

  try {
    const data = (await response.json()) as { code?: unknown; message?: unknown };
    if (typeof data.code === 'string') {
      code = data.code;
    }
    if (typeof data.message === 'string') {
      message = data.message;
    }
  } catch {
    // Non-JSON error bodies fall back to the generic message.
  }

  return new ApiError(response.status, code, message);
}

async function request<T>(url: string, init: RequestInit): Promise<T> {
  const response = await fetch(url, init);

  if (response.status === 401) {
    clearApiKey();
    emitUnauthorized();
    throw await errorFrom(response);
  }

  if (!response.ok) {
    throw await errorFrom(response);
  }

  if (response.status === 204) {
    return undefined as T;
  }

  return (await response.json()) as T;
}

export async function apiGet<T>(
  path: string,
  params?: Record<string, QueryValue>,
  signal?: AbortSignal
): Promise<T> {
  return request<T>(buildUrl(path, params), {
    method: 'GET',
    headers: requestHeaders(false),
    signal
  });
}

export async function apiPost<T>(
  path: string,
  body?: unknown,
  signal?: AbortSignal
): Promise<T> {
  return request<T>(buildUrl(path), {
    method: 'POST',
    headers: requestHeaders(body !== undefined),
    body: body === undefined ? undefined : JSON.stringify(body),
    signal
  });
}
