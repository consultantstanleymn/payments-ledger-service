import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { apiGet, ApiError } from '../api/client';
import { getApiKey } from '../api/session';
import SignInPage from './SignInPage';

vi.mock('../api/client', async () => {
  const actual = await vi.importActual<typeof import('../api/client')>('../api/client');
  return {
    ...actual,
    apiGet: vi.fn()
  };
});

const mockedApiGet = vi.mocked(apiGet);

function renderSignIn() {
  return render(
    <MemoryRouter initialEntries={['/signin']}>
      <Routes>
        <Route path="/signin" element={<SignInPage />} />
        <Route path="/" element={<div>Protected account</div>} />
      </Routes>
    </MemoryRouter>
  );
}

describe('SignInPage', () => {
  beforeEach(() => {
    sessionStorage.clear();
    vi.clearAllMocks();
  });

  it('stores a valid key and redirects to the protected area', async () => {
    const user = userEvent.setup();
    mockedApiGet.mockResolvedValue({ items: [], nextCursor: null });

    renderSignIn();

    await user.type(screen.getByLabelText(/api key/i), 'dev-key-local-only');
    await user.click(screen.getByRole('button', { name: /sign in/i }));

    expect(await screen.findByText('Protected account')).toBeInTheDocument();
    expect(getApiKey()).toBe('dev-key-local-only');
    expect(mockedApiGet).toHaveBeenCalledWith('/ops/accounts', { limit: 1 });
  });

  it('shows an alert when the API key is rejected', async () => {
    const user = userEvent.setup();
    mockedApiGet.mockRejectedValue(new ApiError(401, 'unauthorized', 'Invalid key'));

    renderSignIn();

    await user.type(screen.getByLabelText(/api key/i), 'bad-key');
    await user.click(screen.getByRole('button', { name: /sign in/i }));

    const alert = await screen.findByRole('alert');
    expect(alert).toHaveTextContent('Invalid API key.');
    expect(getApiKey()).toBeNull();
  });
});
