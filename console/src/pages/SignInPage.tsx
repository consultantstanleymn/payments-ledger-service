import { useState } from 'react';
import type { FormEvent } from 'react';
import { useNavigate } from 'react-router-dom';
import { apiGet, ApiError } from '../api/client';
import { clearApiKey, setApiKey } from '../api/session';
import type { AccountSummary, OpsPage } from '../api/types';

export default function SignInPage() {
  const navigate = useNavigate();
  const [apiKey, setApiKeyValue] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const trimmedKey = apiKey.trim();
    if (!trimmedKey) {
      return;
    }

    setError(null);
    setSubmitting(true);
    setApiKey(trimmedKey);

    try {
      await apiGet<OpsPage<AccountSummary>>('/ops/accounts', { limit: 1 });
      navigate('/', { replace: true });
    } catch (err) {
      clearApiKey();
      if (err instanceof ApiError && err.status === 401) {
        setError('Invalid API key.');
      } else {
        setError('Unable to sign in. Check that the console can reach the ledger API.');
      }
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <main>
      <h1>Ledger console sign in</h1>
      <form onSubmit={handleSubmit}>
        <label htmlFor="api-key">API key</label>
        <input
          id="api-key"
          type="password"
          value={apiKey}
          onChange={(event) => setApiKeyValue(event.target.value)}
          autoComplete="current-password"
          disabled={submitting}
        />
        <button type="submit" disabled={apiKey.trim().length === 0 || submitting}>
          Sign in
        </button>
      </form>
      {error ? <div role="alert">{error}</div> : null}
    </main>
  );
}
