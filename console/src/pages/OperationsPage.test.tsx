import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { apiGet } from '../api/client';
import OperationsPage from './OperationsPage';

vi.mock('../api/client', () => ({
  apiGet: vi.fn()
}));

const apiGetMock = vi.mocked(apiGet);
const fetchMock = vi.fn();

function jsonResponse(status: number, body: unknown): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' }
  });
}

function stubDialogMethods() {
  Object.defineProperty(HTMLDialogElement.prototype, 'showModal', {
    configurable: true,
    writable: true,
    value: function (this: HTMLDialogElement) {
      this.open = true;
    }
  });

  Object.defineProperty(HTMLDialogElement.prototype, 'close', {
    configurable: true,
    writable: true,
    value: function (this: HTMLDialogElement) {
      this.open = false;
    }
  });
}

describe('OperationsPage', () => {
  beforeEach(() => {
    apiGetMock.mockReset();
    apiGetMock.mockResolvedValue({
      id: 'rec-1',
      matched: 12,
      missing: 2,
      mismatched: 3,
      unexpected: 0,
      details: JSON.stringify([{ id: 'br-1', reason: 'no match' }])
    });

    fetchMock.mockReset();
    vi.stubGlobal('fetch', fetchMock);
    sessionStorage.clear();
    stubDialogMethods();
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('cancel sends nothing', async () => {
    const user = userEvent.setup();
    render(<OperationsPage />);

    await user.type(screen.getByLabelText('Admin key'), 'secret');
    await user.click(screen.getByRole('button', { name: 'Redrive' }));

    const dialog = screen.getByRole('dialog', { hidden: true }) as HTMLDialogElement;
    await waitFor(() => expect(dialog.open).toBe(true));

    await user.click(screen.getByRole('button', { name: 'Cancel' }));

    expect(fetchMock).not.toHaveBeenCalled();
    expect((screen.getByRole('dialog', { hidden: true }) as HTMLDialogElement).open).toBe(false);
  });

  it('confirm posts with X-Admin-Key', async () => {
    fetchMock.mockResolvedValue(jsonResponse(200, { read: 7, republished: 7 }));
    const user = userEvent.setup();
    render(<OperationsPage />);

    await user.type(screen.getByLabelText('Admin key'), 'secret');
    const maxField = screen.getByLabelText('Maximum messages');
    await user.clear(maxField);
    await user.type(maxField, '7');
    await user.click(screen.getByRole('button', { name: 'Redrive' }));

    await waitFor(() => expect((screen.getByRole('dialog', { hidden: true }) as HTMLDialogElement).open).toBe(true));
    await user.click(screen.getByRole('button', { name: 'Confirm redrive' }));

    await waitFor(() => expect(fetchMock).toHaveBeenCalledTimes(1));
    expect(fetchMock).toHaveBeenCalledWith(
      '/admin/dlt/redrive?max=7',
      expect.objectContaining({
        method: 'POST',
        headers: { Accept: 'application/json', 'X-Admin-Key': 'secret' }
      })
    );
    await screen.findByText('Read 7, republished 7.');
  });

  it('max out of range blocked', async () => {
    const user = userEvent.setup();
    render(<OperationsPage />);

    const maxField = screen.getByLabelText('Maximum messages');
    await user.clear(maxField);
    await user.type(maxField, '1001');
    await user.type(screen.getByLabelText('Admin key'), 'secret');

    expect(screen.getByRole('button', { name: 'Redrive' })).toBeDisabled();
    expect((screen.getByRole('dialog', { hidden: true }) as HTMLDialogElement).open).toBe(false);
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('403 shows message', async () => {
    fetchMock.mockResolvedValue(
      jsonResponse(403, { code: 'forbidden', message: 'Admin key rejected' })
    );
    const user = userEvent.setup();
    render(<OperationsPage />);

    await user.type(screen.getByLabelText('Admin key'), 'bad');
    await user.click(screen.getByRole('button', { name: 'Redrive' }));
    await waitFor(() => expect((screen.getByRole('dialog', { hidden: true }) as HTMLDialogElement).open).toBe(true));
    await user.click(screen.getByRole('button', { name: 'Confirm redrive' }));

    expect(await screen.findByText('Admin key rejected')).toBeInTheDocument();
  });

  it('admin key not in sessionStorage after submit', async () => {
    fetchMock.mockResolvedValue(jsonResponse(200, { read: 2, republished: 2 }));
    const user = userEvent.setup();
    render(<OperationsPage />);

    await user.type(screen.getByLabelText('Admin key'), 'not-stored');
    await user.click(screen.getByRole('button', { name: 'Redrive' }));
    await waitFor(() => expect((screen.getByRole('dialog', { hidden: true }) as HTMLDialogElement).open).toBe(true));
    await user.click(screen.getByRole('button', { name: 'Confirm redrive' }));

    await screen.findByText('Read 2, republished 2.');
    expect(sessionStorage.getItem('adminKey')).toBeNull();
  });
});
