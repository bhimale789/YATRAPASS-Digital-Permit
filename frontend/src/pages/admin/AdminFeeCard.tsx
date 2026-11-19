import { useState, type FormEvent } from 'react';
import { adminApi } from '../../api/endpoints';
import { Alert, Card } from '../../components/ui';
import { useAsync } from '../../hooks/useAsync';
import type { Application } from '../../types';
import { errorMessage } from '../../utils/errors';
import { formatDateTime, formatMoney, humanize } from '../../utils/format';

/** Fee status on the review page, with counter (offline) payment recording. */
export function AdminFeeCard({ app, onChanged }: { app: Application; onChanged: () => void }) {
  const summary = app.payment;
  const history = useAsync(() => adminApi.applicationPayments(app.id), [app.id, summary?.status]);
  const [reference, setReference] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  if (!summary || summary.status === 'NOT_REQUIRED') return null;
  const open = app.status === 'PENDING' || app.status === 'UNDER_REVIEW';

  const record = async (e: FormEvent) => {
    e.preventDefault();
    if (!confirm(`Record ${formatMoney(summary.amount)} paid at the counter (ref ${reference.trim()})?`)) return;
    setBusy(true);
    setError(null);
    try {
      await adminApi.recordOfflinePayment(app.id, reference.trim());
      setReference('');
      onChanged();
    } catch (err) {
      setError(errorMessage(err));
    } finally {
      setBusy(false);
    }
  };

  return (
    <Card title="Permit fee">
      <Alert>{error}</Alert>
      <div className="fee-box">
        <div>
          <div className="fee-amount">{formatMoney(summary.amount, summary.currency)}</div>
          {summary.status === 'PAID' ? (
            <span className="muted small">
              <span className="badge badge-ok">Paid</span>{' '}
              {summary.provider === 'ESEWA' ? 'eSewa' : 'Counter'} · {formatDateTime(summary.paidAt)} · Ref {summary.reference}
            </span>
          ) : (
            <span className={`badge badge-${summary.status === 'PENDING' ? 'info' : 'warn'}`}>
              {summary.status === 'PENDING' ? 'eSewa payment in progress' : 'Not paid'}
            </span>
          )}
        </div>
      </div>
      {open && summary.status !== 'PAID' && (
        <>
          {summary.requiredForApproval && (
            <p className="muted small">Approval is blocked until the fee is paid online or recorded here.</p>
          )}
          <form className="inline-form" onSubmit={record}>
            <label className="field">
              <span className="field-label">Counter receipt number</span>
              <input value={reference} onChange={(e) => setReference(e.target.value)} required maxLength={100}
                placeholder="e.g. RCPT-2026-0042" />
            </label>
            <button className="btn btn-primary" disabled={busy || !reference.trim()}>Record counter payment</button>
          </form>
        </>
      )}
      {!!history.data?.length && (
        <details className="payment-history">
          <summary className="small">Payment attempts ({history.data.length})</summary>
          <ul className="doc-list">
            {history.data.map((p) => (
              <li key={p.id}>
                <div>
                  <strong>{p.provider === 'ESEWA' ? 'eSewa' : 'Counter'} · {humanize(p.status)}</strong>
                  <span className="muted small">
                    {formatDateTime(p.createdAt)}{p.reference && ` · Ref ${p.reference}`}
                    {p.failureReason && ` · ${p.failureReason}`}
                  </span>
                </div>
                <span className="money">{formatMoney(p.amount, p.currency)}</span>
              </li>
            ))}
          </ul>
        </details>
      )}
    </Card>
  );
}
