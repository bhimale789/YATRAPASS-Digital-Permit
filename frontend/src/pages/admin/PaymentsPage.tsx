import { useState } from 'react';
import { Link } from 'react-router-dom';
import { adminApi } from '../../api/endpoints';
import { ResponsiveTable } from '../../components/ResponsiveTable';
import { Alert, Card, EmptyState, PageHeader, Pagination, Spinner } from '../../components/ui';
import { useAsync } from '../../hooks/useAsync';
import type { PaymentProvider, PaymentStatus } from '../../types';
import { formatDateTime, formatMoney, humanize } from '../../utils/format';

const TONE: Record<PaymentStatus, string> = { COMPLETED: 'ok', PENDING: 'info', FAILED: 'bad', CANCELLED: 'muted' };

export function PaymentsPage() {
  const [status, setStatus] = useState<PaymentStatus | ''>('');
  const [provider, setProvider] = useState<PaymentProvider | ''>('');
  const [page, setPage] = useState(0);
  const { data, error, loading } = useAsync(
    () => adminApi.payments({ status: status || undefined, provider: provider || undefined, page, size: 20 }),
    [status, provider, page],
  );

  return (
    <>
      <PageHeader title="Payments" subtitle="Permit fees paid with eSewa or at the counter" />
      <Card>
        <div className="toolbar">
          <select value={status} aria-label="Status" onChange={(e) => { setStatus(e.target.value as PaymentStatus | ''); setPage(0); }}>
            <option value="">All statuses</option>
            <option value="COMPLETED">Completed</option>
            <option value="PENDING">Pending</option>
            <option value="FAILED">Failed</option>
            <option value="CANCELLED">Cancelled</option>
          </select>
          <select value={provider} aria-label="Method" onChange={(e) => { setProvider(e.target.value as PaymentProvider | ''); setPage(0); }}>
            <option value="">All methods</option>
            <option value="ESEWA">eSewa</option>
            <option value="OFFLINE">Counter</option>
          </select>
        </div>
        <Alert>{error}</Alert>
        {loading && !data ? (
          <Spinner />
        ) : !data?.content.length ? (
          <EmptyState>No payments match.</EmptyState>
        ) : (
          <ResponsiveTable>
            <table>
              <thead>
                <tr>
                  <th>Date</th>
                  <th>Application</th>
                  <th>Tourist</th>
                  <th>Method</th>
                  <th>Amount</th>
                  <th>Status</th>
                  <th>Reference</th>
                </tr>
              </thead>
              <tbody>
                {data.content.map((p) => (
                  <tr key={p.id}>
                    <td>{formatDateTime(p.completedAt ?? p.createdAt)}</td>
                    <td>
                      {p.applicationId ? <Link to={`/admin/applications/${p.applicationId}`}>{p.applicationNumber}</Link> : '—'}
                    </td>
                    <td>{p.touristName ?? '—'}</td>
                    <td>{p.provider === 'ESEWA' ? 'eSewa' : 'Counter'}</td>
                    <td className="money">{formatMoney(p.amount, p.currency)}</td>
                    <td>
                      <span className={`badge badge-${TONE[p.status]}`}>{humanize(p.status)}</span>
                      {p.failureReason && <div className="muted small">{p.failureReason}</div>}
                    </td>
                    <td className="mono">{p.reference ?? '—'}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </ResponsiveTable>
        )}
        <Pagination page={data} onChange={setPage} />
      </Card>
    </>
  );
}
