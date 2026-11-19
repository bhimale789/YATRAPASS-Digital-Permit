import { useState } from 'react';
import { ResponsiveTable } from '../../components/ResponsiveTable';
import { Link } from 'react-router-dom';
import { touristApi } from '../../api/endpoints';
import { Alert, Card, EmptyState, PageHeader, Pagination, Spinner, StatusBadge } from '../../components/ui';
import { useAsync } from '../../hooks/useAsync';
import type { ApplicationStatus } from '../../types';
import { formatDate, formatDateTime, humanize } from '../../utils/format';

const STATUSES: ApplicationStatus[] = ['PENDING', 'UNDER_REVIEW', 'APPROVED', 'REJECTED', 'CANCELLED'];

export function ApplicationsPage() {
  const [status, setStatus] = useState<ApplicationStatus | ''>('');
  const [page, setPage] = useState(0);
  const { data, error, loading } = useAsync(
    () => touristApi.applications({ status: status || undefined, page, size: 10 }),
    [status, page],
  );

  return (
    <>
      <PageHeader
        title="My Applications"
        actions={
          <Link className="btn btn-primary" to="/tourist/applications/new">
            + New application
          </Link>
        }
      />
      <Card>
        <div className="toolbar">
          <select
            value={status}
            onChange={(e) => {
              setStatus(e.target.value as ApplicationStatus | '');
              setPage(0);
            }}
            aria-label="Filter by status"
          >
            <option value="">All statuses</option>
            {STATUSES.map((s) => (
              <option key={s} value={s}>
                {humanize(s)}
              </option>
            ))}
          </select>
        </div>
        <Alert>{error}</Alert>
        {loading ? (
          <Spinner />
        ) : !data?.content.length ? (
          <EmptyState>No applications found.</EmptyState>
        ) : (
          <ResponsiveTable>
            <table>
              <thead>
                <tr>
                  <th>Application</th>
                  <th>Destination</th>
                  <th>Travel dates</th>
                  <th>Submitted</th>
                  <th>Status</th>
                  <th>Permit</th>
                </tr>
              </thead>
              <tbody>
                {data.content.map((a) => (
                  <tr key={a.id}>
                    <td>
                      <Link to={`/tourist/applications/${a.id}`}>{a.applicationNumber}</Link>
                    </td>
                    <td>{a.area.name}</td>
                    <td>
                      {formatDate(a.startDate)} – {formatDate(a.endDate)}
                    </td>
                    <td>{formatDateTime(a.submittedAt)}</td>
                    <td>
                      <StatusBadge status={a.status} />
                    </td>
                    <td>{a.permit ? <Link to="/tourist/permits">{a.permit.permitNumber}</Link> : '—'}</td>
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
