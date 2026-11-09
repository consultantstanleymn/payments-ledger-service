import { useEffect, useId, useRef, useState } from 'react';
import type { MouseEvent as ReactMouseEvent } from 'react';
import { apiGet } from '../api/client';
import type { AccountSummary, OpsPage } from '../api/types';
import DataTable, { type DataTableColumn } from './DataTable';
import ErrorBanner from './ErrorBanner';
import LoadMore from './LoadMore';
import styles from './EntriesDrawer.module.scss';

interface AccountEntry {
  entryId: string;
  txnId: string;
  kind: string;
  reference: string;
  amountMinor: number;
  createdAt: string;
}

interface EntriesDrawerProps {
  account: AccountSummary;
  onClose: () => void;
}

function formatMinor(amountMinor: number, currency: string): string {
  const value = BigInt(amountMinor);
  const negative = value < 0n;
  const unsigned = (negative ? -value : value).toString();
  const major = unsigned.length > 2 ? unsigned.slice(0, -2) : '0';
  const minor = unsigned.length > 2 ? unsigned.slice(-2) : unsigned.padStart(2, '0');
  const groupedMajor = major.replace(/\B(?=(\d{3})+(?!\d))/g, ',');
  return `${negative ? '-' : ''}${groupedMajor}.${minor} ${currency}`;
}

function formatTimestamp(createdAt: string): string {
  return new Date(createdAt).toLocaleString();
}

export default function EntriesDrawer({ account, onClose }: EntriesDrawerProps) {
  const titleId = useId();
  const closeButtonRef = useRef<HTMLButtonElement>(null);
  const previousFocusRef = useRef<HTMLElement | null>(null);
  const [entries, setEntries] = useState<AccountEntry[]>([]);
  const [nextCursor, setNextCursor] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [loadingMore, setLoadingMore] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    previousFocusRef.current =
      document.activeElement instanceof HTMLElement ? document.activeElement : null;
    closeButtonRef.current?.focus();

    function handleKeyDown(event: KeyboardEvent) {
      if (event.key === 'Escape') {
        onClose();
      }
    }

    document.addEventListener('keydown', handleKeyDown);
    return () => {
      document.removeEventListener('keydown', handleKeyDown);
      previousFocusRef.current?.focus();
    };
  }, [onClose]);

  useEffect(() => {
    let cancelled = false;
    const controller = new AbortController();

    async function loadFirstPage() {
      try {
        const page = await apiGet<OpsPage<AccountEntry>>(
          `/ops/accounts/${account.id}/entries`,
          { limit: 25 },
          controller.signal
        );

        if (!cancelled) {
          setEntries(page.items);
          setNextCursor(page.nextCursor);
          setLoading(false);
        }
      } catch (err) {
        if (cancelled || controller.signal.aborted) {
          return;
        }

        setError(err instanceof Error ? err.message : 'Unable to load entries.');
        setLoading(false);
      }
    }

    void loadFirstPage();

    return () => {
      cancelled = true;
      controller.abort();
    };
  }, [account.id]);

  async function handleLoadMore() {
    if (!nextCursor || loadingMore) {
      return;
    }

    setLoadingMore(true);

    try {
      const page = await apiGet<OpsPage<AccountEntry>>(
        `/ops/accounts/${account.id}/entries`,
        { limit: 25, cursor: nextCursor }
      );

      setEntries((current) => {
        const existingIds = new Set(current.map((entry) => entry.entryId));
        return [...current, ...page.items.filter((entry) => !existingIds.has(entry.entryId))];
      });
      setNextCursor(page.nextCursor);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Unable to load more entries.');
    } finally {
      setLoadingMore(false);
    }
  }

  function handleOverlayMouseDown(event: ReactMouseEvent<HTMLDivElement>) {
    if (event.target === event.currentTarget) {
      onClose();
    }
  }

  const columns: DataTableColumn<AccountEntry>[] = [
    {
      key: 'entryId',
      header: 'Entry',
      render: (entry) => entry.entryId
    },
    {
      key: 'txnId',
      header: 'Transaction',
      render: (entry) => entry.txnId
    },
    {
      key: 'kind',
      header: 'Kind',
      render: (entry) => entry.kind
    },
    {
      key: 'reference',
      header: 'Reference',
      render: (entry) => entry.reference
    },
    {
      key: 'amountMinor',
      header: 'Amount',
      render: (entry) => formatMinor(entry.amountMinor, account.currency)
    },
    {
      key: 'createdAt',
      header: 'Created',
      render: (entry) => formatTimestamp(entry.createdAt)
    }
  ];

  return (
    <div className={styles.overlay} onMouseDown={handleOverlayMouseDown}>
      <div className={styles.dialog} role="dialog" aria-modal="true" aria-labelledby={titleId}>
        <div className={styles.header}>
          <h2 id={titleId}>{account.name} entries</h2>
          <button
            type="button"
            ref={closeButtonRef}
            className={styles.closeButton}
            onClick={onClose}
          >
            Close
          </button>
        </div>

        <p className={styles.balance}>
          Available: {formatMinor(account.availableMinor, account.currency)}
        </p>

        {error ? <ErrorBanner message={error} /> : null}

        {loading ? (
          <p role="status">Loading entries...</p>
        ) : (
          <>
            <DataTable
              caption={`Journal entries for ${account.name}`}
              columns={columns}
              items={entries}
              getRowKey={(entry) => entry.entryId}
              emptyMessage="No journal entries for this account."
            />
            <LoadMore nextCursor={nextCursor} loading={loadingMore} onClick={handleLoadMore} />
          </>
        )}
      </div>
    </div>
  );
}

