import { useEffect, useState, type FormEvent } from 'react';
import { apiGet } from '../api/client';
import ConfirmDialog from '../components/ConfirmDialog';
import ErrorBanner from '../components/ErrorBanner';
import styles from './OperationsPage.module.scss';

interface ReconciliationReport {
  id: string;
  matched: number;
  missing: number;
  mismatched: number;
  unexpected: number;
  details: string;
}

interface RedriveResult {
  read: number;
  republished: number;
}

type BreakEntry = Record<string, unknown> | string;

function parseBreakEntries(details: string): BreakEntry[] | null {
  try {
    const parsed: unknown = JSON.parse(details);
    if (!Array.isArray(parsed)) {
      return null;
    }
    return parsed as BreakEntry[];
  } catch {
    return null;
  }
}

function describeBreak(entry: BreakEntry): string {
  if (typeof entry === 'string') {
    return entry;
  }

  return Object.entries(entry)
    .map(([key, value]) => `${key}: ${String(value)}`)
    .join(', ');
}

function isNotFoundError(err: unknown): boolean {
  if (typeof err !== 'object' || err === null) {
    return false;
  }

  return 'status' in err && (err as { status?: unknown }).status === 404;
}

export default function OperationsPage() {
  const [report, setReport] = useState<ReconciliationReport | null>(null);
  const [reportLoading, setReportLoading] = useState(true);
  const [reportError, setReportError] = useState<string | null>(null);
  const [noReport, setNoReport] = useState(false);

  const [max, setMax] = useState('10');
  const [adminKey, setAdminKey] = useState('');
  const [dialogOpen, setDialogOpen] = useState(false);
  const [redriveLoading, setRedriveLoading] = useState(false);
  const [redriveError, setRedriveError] = useState<string | null>(null);
  const [redriveResult, setRedriveResult] = useState<RedriveResult | null>(null);

  const maxNumber = Number(max);
  const maxInvalid =
    max.trim() === '' || !Number.isInteger(maxNumber) || maxNumber < 1 || maxNumber > 1000;

  useEffect(() => {
    let cancelled = false;
    const controller = new AbortController();

    async function loadLatestReport() {
      try {
        const data = await apiGet<ReconciliationReport>(
          '/reconciliations/latest',
          undefined,
          controller.signal
        );

        if (!cancelled) {
          setReport(data);
          setNoReport(false);
          setReportLoading(false);
        }
      } catch (err) {
        if (cancelled || controller.signal.aborted) {
          return;
        }

        if (isNotFoundError(err)) {
          setNoReport(true);
        } else {
          setReportError(
            err instanceof Error ? err.message : 'Unable to load the reconciliation report.'
          );
        }

        setReportLoading(false);
      }
    }

    void loadLatestReport();

    return () => {
      cancelled = true;
      controller.abort();
    };
  }, []);

  function handleRedriveSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();

    if (maxInvalid) {
      setRedriveError('Enter a maximum from 1 to 1000.');
      return;
    }

    if (!adminKey.trim()) {
      setRedriveError('Admin key is required.');
      return;
    }

    setRedriveError(null);
    setRedriveResult(null);
    setDialogOpen(true);
  }

  function cancelRedrive() {
    setDialogOpen(false);
    setAdminKey('');
  }

  async function confirmRedrive() {
    setDialogOpen(false);

    const key = adminKey.trim();
    const maximum = maxNumber;

    if (!key) {
      return;
    }

    setRedriveLoading(true);
    setRedriveError(null);
    setRedriveResult(null);

    try {
      const response = await fetch(`/admin/dlt/redrive?max=${encodeURIComponent(String(maximum))}`, {
        method: 'POST',
        headers: {
          Accept: 'application/json',
          'X-Admin-Key': key
        }
      });

      if (!response.ok) {
        let message = `Request failed with status ${response.status}`;

        try {
          const data = (await response.json()) as { code?: unknown; message?: unknown };
          if (typeof data.message === 'string') {
            message = data.message;
          }
        } catch {
          // Non-JSON error bodies fall back to the generic message.
        }

        throw new Error(message);
      }

      const data = (await response.json()) as { read?: unknown; republished?: unknown };
      const read = typeof data.read === 'number' ? data.read : Number(data.read ?? 0);
      const republished =
        typeof data.republished === 'number' ? data.republished : Number(data.republished ?? 0);

      setRedriveResult({ read, republished });
    } catch (err) {
      setRedriveError(
        err instanceof Error ? err.message : 'Unable to redrive the dead-letter topic.'
      );
    } finally {
      setRedriveLoading(false);
      setAdminKey('');
    }
  }

  const breakEntries = report ? parseBreakEntries(report.details) : null;

  return (
    <div className={styles.container}>
      <section className={styles.section} aria-labelledby="reconciliation-heading">
        <h2 id="reconciliation-heading">Latest reconciliation report</h2>

        {reportLoading ? <p role="status">Loading reconciliation report...</p> : null}
        {reportError ? <ErrorBanner message={reportError} /> : null}
        {noReport && !reportLoading ? <p>No reconciliation report yet</p> : null}

        {report ? (
          <>
            <dl className={styles.reportGrid}>
              <div>
                <dt>Matched</dt>
                <dd>{report.matched}</dd>
              </div>
              <div>
                <dt>Missing</dt>
                <dd>{report.missing}</dd>
              </div>
              <div>
                <dt>Mismatched</dt>
                <dd>{report.mismatched}</dd>
              </div>
              <div>
                <dt>Unexpected</dt>
                <dd>{report.unexpected}</dd>
              </div>
            </dl>

            <h3>Break list</h3>
            {breakEntries === null ? (
              <pre className={styles.rawDetails}>{report.details}</pre>
            ) : breakEntries.length === 0 ? (
              <p>No breaks in the latest report.</p>
            ) : (
              <ul className={styles.breakList}>
                {breakEntries.map((entry, index) => (
                  <li key={`${typeof entry === 'string' ? entry : report.id}-${index}`}>
                    {describeBreak(entry)}
                  </li>
                ))}
              </ul>
            )}
          </>
        ) : null}
      </section>

      <section className={styles.section} aria-labelledby="redrive-heading">
        <h2 id="redrive-heading">Dead-letter redrive</h2>

        <form className={styles.form} onSubmit={handleRedriveSubmit}>
          <div className={styles.field}>
            <label htmlFor="admin-key">Admin key</label>
            <input
              id="admin-key"
              type="password"
              autoComplete="off"
              value={adminKey}
              onChange={(event) => setAdminKey(event.target.value)}
            />
          </div>

          <div className={styles.field}>
            <label htmlFor="redrive-max">Maximum messages</label>
            <input
              id="redrive-max"
              type="number"
              min={1}
              max={1000}
              value={max}
              onChange={(event) => setMax(event.target.value)}
            />
          </div>

          <button type="submit" className={styles.redriveButton} disabled={maxInvalid}>
            Redrive
          </button>
        </form>

        {redriveLoading ? <p role="status">Redriving dead-letter messages...</p> : null}
        {redriveError ? <ErrorBanner message={redriveError} /> : null}
        {redriveResult ? (
          <p role="status">
            Read {redriveResult.read}, republished {redriveResult.republished}.
          </p>
        ) : null}
      </section>

      <ConfirmDialog
        open={dialogOpen}
        title="Redrive dead-letter topic?"
        confirmLabel="Confirm redrive"
        confirmDisabled={redriveLoading}
        onCancel={cancelRedrive}
        onConfirm={() => {
          void confirmRedrive();
        }}
      >
        <p>Republish up to {maxNumber} dead-letter messages.</p>
      </ConfirmDialog>
    </div>
  );
}
