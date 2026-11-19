import { useState } from 'react';
import { paymentApi } from '../api/endpoints';
import { useAsync } from '../hooks/useAsync';
import type { Application, EsewaCheckout } from '../types';
import { errorMessage } from '../utils/errors';
import { formatDateTime, formatMoney, humanize } from '../utils/format';
import { Alert, Card } from './ui';

/** Navigates the browser to eSewa by POSTing the signed checkout form. */
export function submitEsewaForm(checkout: EsewaCheckout) {
  const form = document.createElement('form');
  form.method = 'POST';
  form.action = checkout.formUrl;
  form.style.display = 'none';
  Object.entries(checkout.fields).forEach(([name, value]) => {
    const input = document.createElement('input');
    input.type = 'hidden';
    input.name = name;
    input.value = value;
    form.appendChild(input);
  });
  document.body.appendChild(form);
  form.submit();
}

const FEE_LABEL = {
  NOT_REQUIRED: 'No fee',
  UNPAID: 'Not paid',
  PENDING: 'Payment in progress',
  PAID: 'Paid',
} as const;

const FEE_TONE = { NOT_REQUIRED: 'muted', UNPAID: 'warn', PENDING: 'info', PAID: 'ok' } as const;

/** Tourist view of an application's permit fee, with eSewa checkout. */
export function PaymentCard({ app, onChanged }: { app: Application; onChanged: () => void }) {
  const summary = app.payment;
  const history = useAsync(() => paymentApi.forApplication(app.id), [app.id, summary?.status]);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  if (!summary || summary.status === 'NOT_REQUIRED') return null;

  const open = app.status === 'PENDING' || app.status === 'UNDER_REVIEW';
  const online = summary.requiredForApproval;
  const pending = history.data?.find((p) => p.status === 'PENDING');

  const pay = async () => {
    setBusy(true);
    setError(null);
    try {
      submitEsewaForm(await paymentApi.startEsewa(app.id));
      // The page navigates away; keep the button disabled.
    } catch (err) {
      setError(errorMessage(err));
      setBusy(false);
    }
  };

  const check = async () => {
    if (!pending) return;
    setBusy(true);
    setError(null);
    try {
      const result = await paymentApi.check(pending.transactionUuid);
      if (result.status === 'PENDING') setError('eSewa has not confirmed this payment yet. Try again in a minute.');
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
          <span className={`badge badge-${FEE_TONE[summary.status]}`}>{FEE_LABEL[summary.status]}</span>
          {summary.status === 'PAID' && (
            <span className="muted small">
              {' '}via {summary.provider === 'ESEWA' ? 'eSewa' : 'permit office'} · {formatDateTime(summary.paidAt)}
              {summary.reference && <> · Ref {summary.reference}</>}
            </span>
          )}
        </div>
        {open && summary.status !== 'PAID' && online && (
          <div className="row-actions">
            {pending && (
              <button className="btn btn-ghost" onClick={check} disabled={busy}>Check payment</button>
            )}
            <button className="btn btn-esewa" onClick={pay} disabled={busy}>
              {busy ? 'Opening eSewa…' : 'Pay with eSewa'}
            </button>
          </div>
        )}
      </div>
      {open && summary.status !== 'PAID' && (
        <p className="muted small">
          {online
            ? 'Your application is approved only after the fee is paid. You will be redirected to eSewa to pay securely.'
            : 'Pay this fee at the permit office; staff will record the payment.'}
        </p>
      )}
      {!!history.data?.length && (
        <details className="payment-history">
          <summary className="small">Payment attempts ({history.data.length})</summary>
          <ul className="doc-list">
            {history.data.map((p) => (
              <li key={p.id}>
                <div>
                  <strong>{p.provider === 'ESEWA' ? 'eSewa' : 'Permit office'} · {humanize(p.status)}</strong>
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
