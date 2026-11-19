import { useState } from 'react';
import { ResponsiveTable } from '../../components/ResponsiveTable';
import { Link } from 'react-router-dom';
import { adminApi } from '../../api/endpoints';
import { Alert, Card, EmptyState, PageHeader, Pagination, Spinner } from '../../components/ui';
import { useAsync } from '../../hooks/useAsync';
import { useDebounce } from '../../hooks/useDebounce';
import { formatDateTime } from '../../utils/format';

export function AdminTouristsPage() {
  const [q, setQ] = useState('');
  const [page, setPage] = useState(0);
  const debouncedQ = useDebounce(q);
  const { data, error, loading } = useAsync(() => adminApi.tourists({ q: debouncedQ, page, size: 20 }), [debouncedQ, page]);

  return (
    <>
      <PageHeader title="Tourists" />
      <Card>
        <div className="toolbar">
          <input
            type="search"
            placeholder="Name, passport number or email…"
            value={q}
            onChange={(e) => {
              setQ(e.target.value);
              setPage(0);
            }}
            maxLength={100}
          />
        </div>
        <Alert>{error}</Alert>
        {loading && !data ? (
          <Spinner />
        ) : !data?.content.length ? (
          <EmptyState>No tourists match.</EmptyState>
        ) : (
          <ResponsiveTable>
            <table>
              <thead>
                <tr>
                  <th>Name</th>
                  <th>Email</th>
                  <th>Nationality</th>
                  <th>Passport</th>
                  <th>Profile</th>
                  <th>Account</th>
                  <th>Registered</th>
                </tr>
              </thead>
              <tbody>
                {data.content.map((t) => (
                  <tr key={t.id}>
                    <td>
                      <Link to={`/admin/tourists/${t.id}`}>{t.fullName}</Link>
                    </td>
                    <td>{t.email}</td>
                    <td>{t.nationality ?? '—'}</td>
                    <td className="mono">{t.passportNumberMasked ?? '—'}</td>
                    <td>{t.profileComplete ? 'Complete' : 'Incomplete'}</td>
                    <td>{t.enabled ? <span className="badge badge-ok">Enabled</span> : <span className="badge badge-bad">Disabled</span>}</td>
                    <td>{formatDateTime(t.registeredAt)}</td>
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
