import { useState } from 'react';
import { ResponsiveTable } from '../../components/ResponsiveTable';
import { Link, useSearchParams } from 'react-router-dom';
import { adminApi } from '../../api/endpoints';
import { Alert, Card, EmptyState, PageHeader, Pagination, Spinner, StatusBadge } from '../../components/ui';
import { useAsync } from '../../hooks/useAsync';
import { useDebounce } from '../../hooks/useDebounce';
import type { PermitStatus } from '../../types';
import { formatDate } from '../../utils/format';

const TABS: { label: string; status?: PermitStatus }[] = [
  { label: 'All' },
  { label: 'Active', status: 'ACTIVE' },
  { label: 'Expired', status: 'EXPIRED' },
  { label: 'Cancelled', status: 'CANCELLED' },
  { label: 'Completed', status: 'COMPLETED' },
];

export function AdminPermitsPage() {
  const [params, setParams] = useSearchParams();
  const [q, setQ] = useState(params.get('q') ?? '');
  const status = (params.get('status') ?? '') as PermitStatus | '';
  const page = Number(params.get('page') ?? 0);
  const debouncedQ = useDebounce(q);

  const update = (next: Record<string, string | number | undefined>) => {
    const merged = new URLSearchParams(params);
    Object.entries(next).forEach(([k, v]) => (v === undefined || v === '' ? merged.delete(k) : merged.set(k, String(v))));
    setParams(merged, { replace: true });
  };

  const { data, error, loading } = useAsync(
    () => adminApi.permits({ q: debouncedQ, status: status || undefined, page, size: 20 }),
    [debouncedQ, status, page],
  );

  return (
    <>
      <PageHeader title="Permits" subtitle="Active, expired and cancelled permits" />
      <div className="tabs" role="tablist">
        {TABS.map((t) => (
          <button
            key={t.label}
            role="tab"
            aria-selected={(t.status ?? '') === status}
            className={(t.status ?? '') === status ? 'active' : ''}
            onClick={() => update({ status: t.status, page: undefined })}
          >
            {t.label}
          </button>
        ))}
      </div>
      <Card>
        <div className="toolbar">
          <input
            type="search"
            placeholder="Permit no., application no., tourist name, passport, area…"
            value={q}
            onChange={(e) => {
              setQ(e.target.value);
              update({ q: e.target.value, page: undefined });
            }}
            maxLength={100}
          />
        </div>
        <Alert>{error}</Alert>
        {loading && !data ? (
          <Spinner />
        ) : !data?.content.length ? (
          <EmptyState>No permits match.</EmptyState>
        ) : (
          <ResponsiveTable>
            <table>
              <thead>
                <tr>
                  <th>Permit</th>
                  <th>Tourist</th>
                  <th>Area</th>
                  <th>Valid from</th>
                  <th>Valid until</th>
                  <th>Status</th>
                  <th>QR</th>
                </tr>
              </thead>
              <tbody>
                {data.content.map((p) => (
                  <tr key={p.id}>
                    <td>
                      <Link to={`/admin/permits/${p.id}`}>{p.permitNumber}</Link>
                    </td>
                    <td>{p.tourist?.fullName}</td>
                    <td>{p.area.name}</td>
                    <td>{formatDate(p.validFrom)}</td>
                    <td>{formatDate(p.validUntil)}</td>
                    <td>
                      <StatusBadge status={p.status} />
                    </td>
                    <td>{p.qrActive ? 'Active' : 'None'}</td>
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
