import { useEffect, useRef, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { paymentApi } from '../../api/endpoints';
import { Card, Spinner } from '../../components/ui';
import type { Payment } from '../../types';
import { errorMessage } from '../../utils/errors';
import { formatMoney } from '../../utils/format';

/**
 * Landing page after eSewa. On success eSewa appends a signed `data` parameter, which the
 * backend verifies and re-checks with eSewa. On failure/cancel we ask the backend for the
 * transaction's real status (the tourist may still have paid).
 */
export function EsewaReturnPage({ outcome }: { outcome: 'success' | 'failure' }) {
  const [params] = useSearchParams();
  const [payment, setPayment] = useState<Payment | null>(null);
  const [error, setError] = useState<string | null>(null);
  const started = useRef(false);

  useEffect(() => {
    if (started.current) return; // React StrictMode runs effects twice in development
    started.current = true;
    const data = params.get('data');
    const uuid = params.get('uuid');
    const request = outcome === 'success' && data ? paymentApi.confirmEsewa(data)
      : uuid ? paymentApi.check(uuid)
        : Promise.reject(new Error('The payment response is missing.'));
    request.then(setPayment).catch((err) => setError(errorMessage(err)));
  }, [outcome, params]);

  const appLink = payment?.applicationId ? `/tourist/applications/${payment.applicationId}` : '/tourist/applications';

  let icon = '…';
  let title = 'Confirming your payment';
  let text = 'Please wait while we confirm the payment with eSewa.';
  if (error) {
    icon = '⚠️';
    title = 'We could not confirm the payment';
    text = error + ' If money was deducted, it will be matched automatically within a few minutes.';
  } else if (payment?.status === 'COMPLETED') {
    icon = '✅';
    title = 'Payment received';
    text = `${formatMoney(payment.amount, payment.currency)} was paid. Reference: ${payment.reference}. `
      + 'Your application can now be reviewed.';
  } else if (payment?.status === 'PENDING') {
    icon = '⏳';
    title = 'Payment pending';
    text = 'eSewa has not confirmed this payment yet. We will check again automatically; you can also refresh it from your application.';
  } else if (payment) {
    icon = '✖️';
    title = 'Payment not completed';
    text = payment.failureReason ?? 'The payment was cancelled. You have not been charged; you can try again.';
  }

  return (
    <div className="payment-result">
      <Card>
        {!payment && !error ? <Spinner label="Confirming payment with eSewa…" /> : (
          <>
            <div className="result-icon" aria-hidden>{icon}</div>
            <h2>{title}</h2>
            <p className="muted">{text}</p>
            <Link className="btn btn-primary" to={appLink}>Back to application</Link>
          </>
        )}
      </Card>
    </div>
  );
}
