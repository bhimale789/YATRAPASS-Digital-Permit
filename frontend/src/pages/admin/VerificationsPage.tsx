import { Link, useSearchParams } from 'react-router-dom';
import { ResponsiveTable } from '../../components/ResponsiveTable';
import { adminApi } from '../../api/endpoints';
import { Alert, Card, EmptyState, PageHeader, Pagination, Spinner, StatusBadge } from '../../components/ui';
import { useAsync } from '../../hooks/useAsync';
import type { VerificationResult } from '../../types';
import { formatDateTime, humanize } from '../../utils/format';

const RESULTS: VerificationResult[] = ['VALID', 'INVALID', 'EXPIRED', 'CANCELLED'];

/** Converts a yyyy-mm-dd date input to the start of that local day as an ISO instant. */
const dayStart = (d: string) => (d ? new Date(`${d}T00:00:00`).toISOString() : undefined);
const nextDayStart = (d: string) => {
  if (!d) return undefined;
  const date = new Date(`${d}T00:00:00`);
  date.setDate(date.getDate() + 1);
  return date.toISOString();
};

export function VerificationsPage() {
  const [params, setParams] = useSearchParams();
  const result = (params.get('result') ?? '') as VerificationResult | '';
  const permitId = params.get('permitId') ?? '';
  const from = params.get('from') ?? '';
  const to = params.get('to') ?? '';
  const page = Number(params.get('page') ?? 0);

  const update = (next: Record<string, string | number | undefined>) => {
    const merged = new URLSearchParams(params);
    Object.entries(next).forEach(([k, v]) => (v === undefined || v === '' ? merged.delete(k) : merged.set(k, String(v))));
    setParams(merged, { replace: true });
  };

  const { data, error, loading } = useAsync(
    () =>
      adminApi.verifications({
        result: result || undefined,
        permitId: permitId ? Number(permitId) : undefined,
        from: dayStart(from),
        to: nextDayStart(to),
        page,
        size: 25,
      }),
    [result, permitId, from, to, page],
  );

  return (
    <>
      <PageHeader title="Verification History" subtitle="Every QR scan by administrators and officers" />
      <Card>
        <div className="toolbar">
          <select value={result} onChange={(e) => update({ result: e.target.value, page: undefined })} aria-label="Result">
            <option value="">All results</option>
            {RESULTS.map((r) => <option key={r} value={r}>{humanize(r)}</option>)}
          </select>
          <label className="inline">From <input type="date" value={from} onChange={(e) => update({ from: e.target.value, page: undefined })} /></label>
          <label className="inline">To <input type="date" value={to} onChange={(e) => update({ to: e.target.value, page: undefined })} /></label>
          {permitId && (
            <button className="btn btn-ghost btn-sm" onClick={() => update({ permitId: undefined, page: undefined })}>
              Permit #{permitId} ✕
            </button>
          )}
        </div>
        <Alert>{error}</Alert>
        {loading && !data ? (
          <Spinner />
        ) : !data?.content.length ? (
          <EmptyState>No verifications found.</EmptyState>
        ) : (
          <ResponsiveTable>
            <table>
              <thead>
                <tr>
                  <th>Time</th>
                  <th>Result</th>
                  <th>Reason</th>
                  <th>Permit</th>
                  <th>Area</th>
                  <th>Verified by</th>
                  <th>IP</th>
                  <th>Location</th>
                </tr>
              </thead>
              <tbody>
                {data.content.map((v) => (
                  <tr key={v.id}>
                    <td>{formatDateTime(v.verifiedAt)}</td>
                    <td><StatusBadge status={v.result} /></td>
                    <td>{v.failureReason ? humanize(v.failureReason) : '—'}</td>
                    <td>{v.permitId ? <Link to={`/admin/permits/${v.permitId}`}>{v.permitNumber}</Link> : '—'}</td>
                    <td>{v.area ?? '—'}</td>
                    <td>{v.verifiedByEmail}</td>
                    <td className="mono">{v.ipAddress ?? '—'}</td>
                    <td className="mono small">{v.latitude != null ? `${v.latitude}, ${v.longitude}` : '—'}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </ResponsiveTable>
        )}
        <Pagination page={data} onChange={(p) => update({ page: p })} />
      </Card>
    </>
  );
}
