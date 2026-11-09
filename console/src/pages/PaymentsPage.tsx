import { useCallback, useEffect, useRef, useState, type FormEvent } from 'react';
import { ApiError, apiGet } from '../api/client';
import type { OpsPage } from '../api/types';
import DataTable, { type DataTableColumn } from '../components/DataTable';
import ErrorBanner from '../components/ErrorBanner';
import LoadMore from '../components/LoadMore';
import { formatMinor } from '../lib/money';
import StatusBadge from '../components/StatusBadge';
import styles from './PaymentsPage.module.scss';

export interface PaymentSummary {
  id: string;
  externalRef: string;
  status: string;
  currency: string;
  amountMinor: number;
  authorizedMinor: number;
  capturedMinor: number;
  refundedMinor: number;
  createdAt: string;
}

type PaymentLookupPayload = PaymentSummary | PaymentSummary[] | { items: PaymentSummary[] } | null;

function formatTimestamp(createdAt: string): string {
  return new Date(createdAt).toLocaleString();
}

function extractPayments(payload: PaymentLookupPayload): PaymentSummary[] {
  if (!payload) {
    return [];
  }

  if (Array.isArray(payload)) {
    return payload;
  }

  if ('items' in payload && Array.isArray(payload.items)) {
    return payload.items;
  }

  return [payload as PaymentSummary];
}

const STATUS_OPTIONS = [
  { value: '', label: 'All statuses' },
  { value: 'AUTHORIZED', label: 'Authorized' },
  { value: 'CAPTURED', label: 'Captured' },
  { value: 'VOIDED', label: 'Voided' }
];

export default function PaymentsPage() {
  const [payments, setPayments] = useState<PaymentSummary[]>([]);
  const [nextCursor, setNextCursor] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [loadingMore, setLoadingMore] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [status, setStatus] = useState('');
  const [lookupRef, setLookupRef] = useState('');
  const [lookupError, setLookupError] = useState<string | null>(null);
  const [lookupLoading, setLookupLoading] = useState(false);
  const [selectedPayment, setSelectedPayment] = useState<PaymentSummary | null>(null);
  const requestIdRef = useRef(0);

  useEffect(() => {
    const controller = new AbortController();
    const requestId = ++requestIdRef.current;

    async function loadFirstPage() {
      try {
        const page = await apiGet<OpsPage<PaymentSummary>>(
          '/ops/payments',
          { limit: 25, status: status || undefined, cursor: undefined },
          controller.signal
        );

        if (controller.signal.aborted || requestId !== requestIdRef.current) {
          return;
        }

        setPayments(page.items);
        setNextCursor(page.nextCursor);
        setLoading(false);
      } catch (err) {
        if (controller.signal.aborted || requestId !== requestIdRef.current) {
          return;
        }

        setError(err instanceof Error ? err.message : 'Unable to load payments.');
        setLoading(false);
      }
    }

    void loadFirstPage();

    return () => controller.abort();
  }, [status]);

  function handleStatusChange(nextStatus: string) {
    if (nextStatus === status) {
      return;
    }

    setStatus(nextStatus);
    setLoading(true);
    setError(null);
    setSelectedPayment(null);
  }

  async function handleLoadMore() {
    if (!nextCursor || loadingMore) {
      return;
    }

    setLoadingMore(true);

    try {
      const page = await apiGet<OpsPage<PaymentSummary>>('/ops/payments', {
        limit: 25,
        status: status || undefined,
        cursor: nextCursor
      });

      setPayments((current) => {
        const existingIds = new Set(current.map((payment) => payment.id));
        return [...current, ...page.items.filter((payment) => !existingIds.has(payment.id))];
      });
      setNextCursor(page.nextCursor);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Unable to load more payments.');
    } finally {
      setLoadingMore(false);
    }
  }

  async function handleLookup(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();

    const reference = lookupRef.trim();
    if (!reference) {
      return;
    }

    setLookupLoading(true);
    setLookupError(null);
    setSelectedPayment(null);

    try {
      const payload = await apiGet<PaymentLookupPayload>('/payments', { externalRef: reference });
      const matches = extractPayments(payload);

      if (matches.length === 0) {
        setLookupError('Payment not found.');
      } else {
        setSelectedPayment(matches[0]);
      }
    } catch (err) {
      if (err instanceof ApiError && err.status === 404) {
        setLookupError('Payment not found.');
      } else {
        setLookupError(err instanceof Error ? err.message : 'Unable to look up payment.');
      }
    } finally {
      setLookupLoading(false);
    }
  }

  const closeDetail = useCallback(() => {
    setSelectedPayment(null);
  }, []);

  const columns: DataTableColumn<PaymentSummary>[] = [
    {
      key: 'externalRef',
      header: 'Reference',
      render: (payment) => (
        <button
          type="button"
          className={styles.referenceButton}
          onClick={() => setSelectedPayment(payment)}
        >
          {payment.externalRef}
        </button>
      )
    },
    {
      key: 'status',
      header: 'Status',
      render: (payment) => <StatusBadge status={payment.status} />
    },
    {
      key: 'currency',
      header: 'Currency',
      render: (payment) => payment.currency
    },
    {
      key: 'authorizedMinor',
      header: 'Authorized',
      render: (payment) => formatMinor(payment.authorizedMinor, payment.currency)
    },
    {
      key: 'capturedMinor',
      header: 'Captured',
      render: (payment) => formatMinor(payment.capturedMinor, payment.currency)
    },
    {
      key: 'createdAt',
      header: 'Created',
      render: (payment) => formatTimestamp(payment.createdAt)
    }
  ];

  const timeline = selectedPayment
    ? [
        { id: 'authorized', label: 'Authorized', amount: selectedPayment.authorizedMinor },
        { id: 'captured', label: 'Captured', amount: selectedPayment.capturedMinor },
        { id: 'refunded', label: 'Refunded', amount: selectedPayment.refundedMinor }
      ].filter((entry) => entry.amount > 0)
    : [];

  return (
    <>
      <div className={styles.toolbar}>
        <fieldset className={styles.radioGroup}>
          <legend>Status</legend>
          <div className={styles.radioOptions}>
            {STATUS_OPTIONS.map((option) => (
              <label key={option.value} className={styles.radioLabel}>
                <input
                  type="radio"
                  name="payment-status"
                  value={option.value}
                  checked={status === option.value}
                  onChange={() => handleStatusChange(option.value)}
                />
                {option.label}
              </label>
            ))}
          </div>
        </fieldset>

        <form className={styles.lookupForm} onSubmit={handleLookup}>
          <div className={styles.field}>
            <label htmlFor="payment-reference">External reference</label>
            <input
              id="payment-reference"
              type="search"
              value={lookupRef}
              onChange={(event) => setLookupRef(event.target.value)}
              placeholder="Payment external reference"
            />
          </div>
          <button
            type="submit"
            className={styles.lookupButton}
            disabled={lookupLoading || !lookupRef.trim()}
          >
            {lookupLoading ? 'Looking up...' : 'Look up'}
          </button>
        </form>
      </div>

      {error ? <ErrorBanner message={error} /> : null}
      {lookupError ? <ErrorBanner message={lookupError} /> : null}

      {loading ? (
        <p role="status">Loading payments...</p>
      ) : (
        <>
          <DataTable
            caption="Payments"
            columns={columns}
            items={payments}
            getRowKey={(payment) => payment.id}
            emptyMessage="No payments match the current filters."
          />
          <LoadMore nextCursor={nextCursor} loading={loadingMore} onClick={handleLoadMore} />
        </>
      )}

      {selectedPayment ? (
        <aside
          className={styles.detailPanel}
          aria-label={`Payment ${selectedPayment.externalRef} details`}
        >
          <div className={styles.detailHeader}>
            <h2>Payment {selectedPayment.externalRef} details</h2>
            <button type="button" onClick={closeDetail}>
              Close
            </button>
          </div>
          <dl className={styles.detailList}>
            <div>
              <dt>Status</dt>
              <dd>
                <StatusBadge status={selectedPayment.status} />
              </dd>
            </div>
            <div>
              <dt>Authorized</dt>
              <dd>{formatMinor(selectedPayment.authorizedMinor, selectedPayment.currency)}</dd>
            </div>
            <div>
              <dt>Captured</dt>
              <dd>{formatMinor(selectedPayment.capturedMinor, selectedPayment.currency)}</dd>
            </div>
            <div>
              <dt>Refunded</dt>
              <dd>{formatMinor(selectedPayment.refundedMinor, selectedPayment.currency)}</dd>
            </div>
          </dl>
          <h3>Timeline</h3>
          <ol className={styles.timeline}>
            {timeline.map((entry) => (
              <li key={entry.id}>
                {entry.label}: {formatMinor(entry.amount, selectedPayment.currency)}
              </li>
            ))}
          </ol>
        </aside>
      ) : null}
    </>
  );
}
