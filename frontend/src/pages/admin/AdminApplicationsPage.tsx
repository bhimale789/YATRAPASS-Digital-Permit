import { useState } from 'react';
import { ResponsiveTable } from '../../components/ResponsiveTable';
import { Link, useSearchParams } from 'react-router-dom';
import { adminApi } from '../../api/endpoints';
import { Alert, Card, EmptyState, PageHeader, Pagination, Spinner, StatusBadge } from '../../components/ui';
import { useAsync } from '../../hooks/useAsync';
import { useDebounce } from '../../hooks/useDebounce';
import type { ApplicationStatus } from '../../types';
import { formatDate, formatDateTime, humanize } from '../../utils/format';

const STATUSES: ApplicationStatus[] = ['PENDING', 'UNDER_REVIEW', 'APPROVED', 'REJECTED', 'CANCELLED'];

export function AdminApplicationsPage() {
  const [params, setParams] = useSearchParams();
  const [q, setQ] = useState(params.get('q') ?? '');
  const status = (params.get('status') ?? '') as ApplicationStatus | '';
  const page = Number(params.get('page') ?? 0);
  const debouncedQ = useDebounce(q);

  const update = (next: Record<string, string | number | undefined>) => {
    const merged = new URLSearchParams(params);
    Object.entries(next).forEach(([k, v]) => (v === undefined || v === '' ? merged.delete(k) : merged.set(k, String(v))));
    setParams(merged, { replace: true });
  };

  const { data, error, loading } = useAsync(
    () => adminApi.applications({ q: debouncedQ, status: status || undefined, page, size: 20 }),
    [debouncedQ, status, page],
  );

  return (
    <>
      <PageHeader title="Permit Applications" subtitle="Review submitted applications and documents" />
      <Card>
        <div className="toolbar">
          <input
            type="search"
            placeholder="Application no., tourist name, passport, area…"
            value={q}
            onChange={(e) => {
              setQ(e.target.value);
              update({ q: e.target.value, page: undefined });
            }}
            maxLength={100}
          />
          <select value={status} onChange={(e) => update({ status: e.target.value, page: undefined })} aria-label="Status">
            <option value="">All statuses</option>
            {STATUSES.map((s) => (
              <option key={s} value={s}>
                {humanize(s)}
              </option>
            ))}
          </select>
        </div>
        <Alert>{error}</Alert>
        {loading && !data ? (
          <Spinner />
        ) : !data?.content.length ? (
          <EmptyState>No applications match.</EmptyState>
        ) : (
          <ResponsiveTable>
            <table>
              <thead>
                <tr>
                  <th>Application</th>
                  <th>Tourist</th>
                  <th>Passport</th>
                  <th>Area</th>
                  <th>Travel dates</th>
                  <th>Submitted</th>
                  <th>Status</th>
                </tr>
              </thead>
              <tbody>
                {data.content.map((a) => (
                  <tr key={a.id}>
                    <td>
                      <Link to={`/admin/applications/${a.id}`}>{a.applicationNumber}</Link>
                    </td>
                    <td>{a.tourist?.fullName}</td>
                    <td className="mono">{a.tourist?.passportNumberMasked}</td>
                    <td>{a.area.name}</td>
                    <td>
                      {formatDate(a.startDate)} – {formatDate(a.endDate)}
                    </td>
                    <td>{formatDateTime(a.submittedAt)}</td>
                    <td>
                      <StatusBadge status={a.status} />
                    </td>
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
