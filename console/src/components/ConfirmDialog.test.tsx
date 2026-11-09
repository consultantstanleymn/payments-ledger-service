import { cleanup, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import ConfirmDialog from './ConfirmDialog';

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

describe('ConfirmDialog', () => {
  beforeEach(() => {
    stubDialogMethods();
  });

  afterEach(() => {
    cleanup();
    vi.restoreAllMocks();
  });

  it('opens with showModal and focuses cancel', () => {
    render(
      <ConfirmDialog open title="Clear report?" onCancel={() => {}} onConfirm={() => {}}>
        <p>Are you sure?</p>
      </ConfirmDialog>
    );

    const dialog = screen.getByRole('dialog') as HTMLDialogElement;
    expect(dialog.open).toBe(true);
    expect(screen.getByRole('button', { name: 'Cancel' })).toHaveFocus();
  });

  it('calls onCancel when Cancel is clicked', async () => {
    const user = userEvent.setup();
    const onCancel = vi.fn();

    render(
      <ConfirmDialog open title="Clear report?" onCancel={onCancel} onConfirm={() => {}}>
        <p>Are you sure?</p>
      </ConfirmDialog>
    );

    await user.click(screen.getByRole('button', { name: 'Cancel' }));
    expect(onCancel).toHaveBeenCalledTimes(1);
  });

  it('calls onConfirm when Confirm is clicked', async () => {
    const user = userEvent.setup();
    const onConfirm = vi.fn();

    render(
      <ConfirmDialog open title="Clear report?" onCancel={() => {}} onConfirm={onConfirm}>
        <p>Are you sure?</p>
      </ConfirmDialog>
    );

    await user.click(screen.getByRole('button', { name: 'Confirm' }));
    expect(onConfirm).toHaveBeenCalledTimes(1);
  });

  it('keeps confirm disabled while loading', () => {
    render(
      <ConfirmDialog
        open
        title="Clear report?"
        confirmDisabled
        onCancel={() => {}}
        onConfirm={() => {}}
      >
        <p>Are you sure?</p>
      </ConfirmDialog>
    );

    expect(screen.getByRole('button', { name: 'Confirm' })).toBeDisabled();
  });
});
