import { useCallback, useEffect, useRef, useState } from 'react';
import { apiGet } from '../api/client';
import type { AccountSummary, OpsPage } from '../api/types';
import DataTable, { type DataTableColumn } from '../components/DataTable';
import EntriesDrawer from '../components/EntriesDrawer';
import ErrorBanner from '../components/ErrorBanner';
import LoadMore from '../components/LoadMore';
import styles from './AccountsPage.module.scss';

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

export default function AccountsPage() {
  const [accounts, setAccounts] = useState<AccountSummary[]>([]);
  const [nextCursor, setNextCursor] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [loadingMore, setLoadingMore] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [currency, setCurrency] = useState('');
  const [query, setQuery] = useState('');
  const [debouncedQuery, setDebouncedQuery] = useState('');
  const [selectedAccount, setSelectedAccount] = useState<AccountSummary | null>(null);
  const debouncedQueryRef = useRef(debouncedQuery);
  const isFirstDebounce = useRef(true);
  const requestIdRef = useRef(0);

  useEffect(() => {
    if (isFirstDebounce.current) {
      isFirstDebounce.current = false;
      return;
    }

    const timerId = window.setTimeout(() => {
      if (debouncedQueryRef.current !== query) {
        debouncedQueryRef.current = query;
        setLoading(true);
        setError(null);
        setDebouncedQuery(query);
      }
    }, 300);

    return () => window.clearTimeout(timerId);
  }, [query]);

  useEffect(() => {
    const controller = new AbortController();
    const requestId = ++requestIdRef.current;

    async function loadFirstPage() {
      try {
        const page = await apiGet<OpsPage<AccountSummary>>(
          '/ops/accounts',
          {
            limit: 25,
            currency: currency || undefined,
            q: debouncedQuery || undefined,
            cursor: undefined
          },
          controller.signal
        );

        if (controller.signal.aborted || requestId !== requestIdRef.current) {
          return;
        }

        setAccounts(page.items);
        setNextCursor(page.nextCursor);
        setLoading(false);
      } catch (err) {
        if (controller.signal.aborted || requestId !== requestIdRef.current) {
          return;
        }

        setError(err instanceof Error ? err.message : 'Unable to load accounts.');
        setLoading(false);
      }
    }

    void loadFirstPage();

    return () => controller.abort();
  }, [currency, debouncedQuery]);

  function handleCurrencyChange(value: string) {
    setCurrency(value);
    setLoading(true);
    setError(null);
  }

  function handleQueryChange(value: string) {
    setQuery(value);
  }

  async function handleLoadMore() {
    if (!nextCursor || loadingMore) {
      return;
    }

    setLoadingMore(true);

    try {
      const page = await apiGet<OpsPage<AccountSummary>>('/ops/accounts', {
        limit: 25,
        currency: currency || undefined,
        q: debouncedQuery || undefined,
        cursor: nextCursor
      });

      setAccounts((current) => {
        const existingIds = new Set(current.map((account) => account.id));
        return [...current, ...page.items.filter((account) => !existingIds.has(account.id))];
      });
      setNextCursor(page.nextCursor);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Unable to load more accounts.');
    } finally {
      setLoadingMore(false);
    }
  }

  const closeAccountEntries = useCallback(() => {
    setSelectedAccount(null);
  }, []);

  const columns: DataTableColumn<AccountSummary>[] = [
    {
      key: 'name',
      header: 'Account',
      render: (account) => (
        <button
          type="button"
          className={styles.accountButton}
          onClick={() => setSelectedAccount(account)}
        >
          {account.name}
        </button>
      )
    },
    {
      key: 'currency',
      header: 'Currency',
      render: (account) => account.currency
    },
    {
      key: 'availableMinor',
      header: 'Available',
      render: (account) => formatMinor(account.availableMinor, account.currency)
    },
    {
      key: 'postedMinor',
      header: 'Posted',
      render: (account) => formatMinor(account.postedMinor, account.currency)
    },
    {
      key: 'heldMinor',
      header: 'Held',
      render: (account) => formatMinor(account.heldMinor, account.currency)
    },
    {
      key: 'floorMinor',
      header: 'Floor',
      render: (account) => formatMinor(account.floorMinor, account.currency)
    },
    {
      key: 'createdAt',
      header: 'Created',
      render: (account) => formatTimestamp(account.createdAt)
    }
  ];

  return (
    <>
      <div className={styles.toolbar}>
        <div className={styles.field}>
          <label htmlFor="account-currency">Currency</label>
          <select
            id="account-currency"
            value={currency}
            onChange={(event) => handleCurrencyChange(event.target.value)}
          >
            <option value="">All currencies</option>
            <option value="USD">USD</option>
            <option value="EUR">EUR</option>
          </select>
        </div>

        <div className={styles.field}>
          <label htmlFor="account-search">Search accounts</label>
          <input
            id="account-search"
            type="search"
            value={query}
            onChange={(event) => handleQueryChange(event.target.value)}
            maxLength={64}
            placeholder="Account name prefix"
          />
        </div>
      </div>

      {error ? <ErrorBanner message={error} /> : null}

      {loading ? (
        <p role="status">Loading accounts...</p>
      ) : (
        <>
          <DataTable
            caption="Accounts"
            columns={columns}
            items={accounts}
            getRowKey={(account) => account.id}
            emptyMessage="No accounts match the current filters."
          />
          <LoadMore nextCursor={nextCursor} loading={loadingMore} onClick={handleLoadMore} />
        </>
      )}

      {selectedAccount ? (
        <EntriesDrawer
          key={selectedAccount.id}
          account={selectedAccount}
          onClose={closeAccountEntries}
        />
      ) : null}
    </>
  );
}

