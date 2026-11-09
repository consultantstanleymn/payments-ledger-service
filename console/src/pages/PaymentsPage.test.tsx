import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ApiError, apiGet } from '../api/client';
import type { OpsPage } from '../api/types';
import PaymentsPage, { type PaymentSummary } from './PaymentsPage';

vi.mock('../api/client', async () => {
  const actual = await vi.importActual<typeof import('../api/client')>('../api/client');
  return {
    ...actual,
    apiGet: vi.fn()
  };
});

const mockedApiGet = vi.mocked(apiGet);

function makePayment(
  id: string,
  externalRef: string,
  overrides: Partial<PaymentSummary> = {}
): PaymentSummary {
  return {
    id,
    externalRef,
    status: 'AUTHORIZED',
    currency: 'USD',
    amountMinor: 1000,
    authorizedMinor: 10000,
    capturedMinor: 0,
    refundedMinor: 0,
    createdAt: '2025-01-01T00:00:00Z',
    ...overrides
  };
}

function paymentsPage(
  items: PaymentSummary[],
  nextCursor: string | null
): OpsPage<PaymentSummary> {
  return { items, nextCursor };
}

const authorized = makePayment('pay-1', 'ref-1', { authorizedMinor: 10000 });
const captured = makePayment('pay-2', 'ref-2', {
  status: 'CAPTURED',
  authorizedMinor: 10000,
  capturedMinor: 90000
});

describe('PaymentsPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('filters by status', async () => {
    mockedApiGet
      .mockResolvedValueOnce(paymentsPage([authorized], null))
      .mockResolvedValueOnce(paymentsPage([captured], null));
    const user = userEvent.setup();

    render(<PaymentsPage />);

    expect(await screen.findByRole('button', { name: 'ref-1' })).toBeInTheDocument();
    await user.click(screen.getByRole('radio', { name: 'Captured' }));

    expect(await screen.findByRole('button', { name: 'ref-2' })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'ref-1' })).not.toBeInTheDocument();
    expect(mockedApiGet).toHaveBeenNthCalledWith(
      2,
      '/ops/payments',
      expect.objectContaining({ status: 'CAPTURED', cursor: undefined }),
      expect.any(AbortSignal)
    );
  });

  it('lookup shows payment', async () => {
    mockedApiGet
      .mockResolvedValueOnce(paymentsPage([], null))
      .mockResolvedValueOnce(captured);
    const user = userEvent.setup();

    render(<PaymentsPage />);

    await screen.findByText('No payments match the current filters.');
    await user.type(screen.getByLabelText(/external reference/i), 'ref-2');
    await user.click(screen.getByRole('button', { name: 'Look up' }));

    expect(
      await screen.findByRole('heading', { name: 'Payment ref-2 details' })
    ).toBeInTheDocument();
    expect(screen.getByText('CAPTURED')).toBeInTheDocument();
    expect(mockedApiGet).toHaveBeenCalledWith('/payments', { externalRef: 'ref-2' });
  });

  it('lookup unknown ref shows not found', async () => {
    mockedApiGet
      .mockResolvedValueOnce(paymentsPage([], null))
      .mockRejectedValueOnce(new ApiError(404, 'not_found', 'Payment not found'));
    const user = userEvent.setup();

    render(<PaymentsPage />);

    await screen.findByText('No payments match the current filters.');
    await user.type(screen.getByLabelText(/external reference/i), 'missing');
    await user.click(screen.getByRole('button', { name: 'Look up' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('Payment not found.');
  });
});
