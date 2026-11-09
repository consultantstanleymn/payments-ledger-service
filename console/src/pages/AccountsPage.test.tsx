import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { apiGet, ApiError } from '../api/client';
import type { AccountSummary, OpsPage } from '../api/types';
import AccountsPage from './AccountsPage';

vi.mock('../api/client', async () => {
  const actual = await vi.importActual<typeof import('../api/client')>('../api/client');
  return {
    ...actual,
    apiGet: vi.fn()
  };
});

const mockedApiGet = vi.mocked(apiGet);

function makeAccount(id: string, name: string): AccountSummary {
  return {
    id,
    name,
    currency: 'USD',
    postedMinor: 1000,
    heldMinor: 200,
    availableMinor: 800,
    floorMinor: 0,
    createdAt: '2025-01-01T00:00:00Z'
  };
}

const alpha = makeAccount('acc-1', 'Alpha');
const beta = makeAccount('acc-2', 'Beta');

function accountsPage(
  items: AccountSummary[],
  nextCursor: string | null
): OpsPage<AccountSummary> {
  return { items, nextCursor };
}

describe('AccountsPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.useRealTimers();
  });

  it('loads first page', async () => {
    mockedApiGet.mockResolvedValueOnce(accountsPage([alpha], null));

    render(<AccountsPage />);

    expect(await screen.findByRole('button', { name: 'Alpha' })).toBeInTheDocument();
    expect(mockedApiGet).toHaveBeenCalledWith(
      '/ops/accounts',
      expect.objectContaining({ limit: 25, cursor: undefined }),
      expect.any(AbortSignal)
    );
  });

  it('load more appends without duplicates', async () => {
    mockedApiGet
      .mockResolvedValueOnce(accountsPage([alpha], 'next-cursor'))
      .mockResolvedValueOnce(accountsPage([alpha, beta], null));
    const user = userEvent.setup();

    render(<AccountsPage />);

    await screen.findByRole('button', { name: 'Alpha' });
    await user.click(screen.getByRole('button', { name: 'Load more' }));

    await screen.findByRole('button', { name: 'Beta' });
    expect(screen.getAllByRole('row')).toHaveLength(3);
    expect(mockedApiGet).toHaveBeenNthCalledWith(
      2,
      '/ops/accounts',
      expect.objectContaining({ cursor: 'next-cursor' })
    );
  });

  it('filter resets cursor', async () => {
    mockedApiGet
      .mockResolvedValueOnce(accountsPage([alpha], 'next-cursor'))
      .mockResolvedValueOnce(accountsPage([beta], null));
    const user = userEvent.setup();

    render(<AccountsPage />);

    await screen.findByRole('button', { name: 'Alpha' });
    await user.selectOptions(screen.getByLabelText(/currency/i), 'EUR');

    await screen.findByRole('button', { name: 'Beta' });
    expect(mockedApiGet).toHaveBeenNthCalledWith(
      2,
      '/ops/accounts',
      expect.objectContaining({ currency: 'EUR', cursor: undefined }),
      expect.any(AbortSignal)
    );
    expect(screen.queryByRole('button', { name: 'Alpha' })).not.toBeInTheDocument();
  });

  it('search debounces', async () => {
    mockedApiGet.mockResolvedValue(accountsPage([], null));
    const user = userEvent.setup();

    render(<AccountsPage />);

    await screen.findByText('No accounts match the current filters.');

    const search = screen.getByLabelText(/search accounts/i);
    await user.type(search, 'a');
    await user.type(search, 'b');

    await waitFor(() => {
      expect(mockedApiGet).toHaveBeenCalledWith(
        '/ops/accounts',
        expect.objectContaining({ q: 'ab' }),
        expect.any(AbortSignal)
      );
    });

    expect(mockedApiGet.mock.calls.filter(([path]) => path === '/ops/accounts')).toHaveLength(2);
  });

  it('drawer opens and returns focus', async () => {
    mockedApiGet
      .mockResolvedValueOnce(accountsPage([alpha], null))
      .mockResolvedValueOnce({ items: [], nextCursor: null });
    const user = userEvent.setup();

    render(<AccountsPage />);

    const accountButton = await screen.findByRole('button', { name: 'Alpha' });
    await user.click(accountButton);

    expect(await screen.findByRole('dialog', { name: /Alpha entries/i })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Close' })).toHaveFocus();

    await user.click(screen.getByRole('button', { name: 'Close' }));

    await waitFor(() => {
      expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    });
    expect(accountButton).toHaveFocus();
  });

  it('error banner on 500', async () => {
    mockedApiGet.mockRejectedValue(new ApiError(500, 'internal_error', 'Ledger unavailable'));

    render(<AccountsPage />);

    const alert = await screen.findByRole('alert');
    expect(alert).toHaveTextContent('Ledger unavailable');
  });
});

