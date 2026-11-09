const API_KEY_STORAGE_KEY = 'ledger.console.apiKey';
const UNAUTHORIZED_EVENT = 'ledger.console.apiUnauthorized';

export function getApiKey(): string | null {
  try {
    return sessionStorage.getItem(API_KEY_STORAGE_KEY);
  } catch {
    return null;
  }
}

export function setApiKey(apiKey: string): void {
  sessionStorage.setItem(API_KEY_STORAGE_KEY, apiKey);
}

export function clearApiKey(): void {
  sessionStorage.removeItem(API_KEY_STORAGE_KEY);
}

export function emitUnauthorized(): void {
  window.dispatchEvent(new Event(UNAUTHORIZED_EVENT));
}

export function onUnauthorized(listener: () => void): () => void {
  window.addEventListener(UNAUTHORIZED_EVENT, listener);
  return () => window.removeEventListener(UNAUTHORIZED_EVENT, listener);
}
