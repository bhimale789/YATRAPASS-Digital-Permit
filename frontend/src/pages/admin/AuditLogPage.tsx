import { useState } from 'react';
import { ResponsiveTable } from '../../components/ResponsiveTable';
import { adminApi } from '../../api/endpoints';
import { Alert, Card, EmptyState, PageHeader, Pagination, Spinner } from '../../components/ui';
import { useAsync } from '../../hooks/useAsync';
import { formatDateTime, humanize } from '../../utils/format';

const ACTIONS = [
  'ADMIN_LOGIN', 'USER_REGISTERED', 'TOURIST_UPDATED', 'AREA_CREATED', 'AREA_UPDATED', 'APPLICATION_SUBMITTED',
  'APPLICATION_CANCELLED', 'APPLICATION_REVIEW_STARTED', 'DOCUMENT_UPLOADED', 'DOCUMENT_VERIFIED', 'PERMIT_APPROVED',
  'PERMIT_REJECTED', 'PERMIT_CANCELLED', 'PERMIT_COMPLETED', 'PERMIT_EXPIRED', 'QR_GENERATED', 'QR_REVOKED', 'QR_VERIFIED',
];

export function AuditLogPage() {
  const [action, setAction] = useState('');
  const [page, setPage] = useState(0);
  const { data, error, loading } = useAsync(
    () => adminApi.auditLogs({ action: action || undefined, page, size: 50 }),
    [action, page],
  );

  return (
    <>
      <PageHeader title="Audit Log" subtitle="Important administrative and security events" />
      <Card>
        <div className="toolbar">
          <select value={action} onChange={(e) => { setAction(e.target.value); setPage(0); }} aria-label="Action">
            <option value="">All actions</option>
            {ACTIONS.map((a) => <option key={a} value={a}>{humanize(a)}</option>)}
          </select>
        </div>
        <Alert>{error}</Alert>
        {loading && !data ? (
          <Spinner />
        ) : !data?.content.length ? (
          <EmptyState>No audit entries.</EmptyState>
        ) : (
          <ResponsiveTable>
            <table>
              <thead>
                <tr>
                  <th>Time</th>
                  <th>Action</th>
                  <th>Actor</th>
                  <th>Entity</th>
                  <th>IP</th>
                  <th>Details</th>
                </tr>
              </thead>
              <tbody>
                {data.content.map((e) => (
                  <tr key={e.id}>
                    <td>{formatDateTime(e.createdAt)}</td>
                    <td>{humanize(e.action)}</td>
                    <td>{e.actorId ? `#${e.actorId} (${e.actorRole})` : e.actorRole ?? 'System'}</td>
                    <td>{e.entityType ? `${e.entityType} #${e.entityId ?? ''}` : '—'}</td>
                    <td className="mono">{e.ipAddress ?? '—'}</td>
                    <td className="mono small">{e.metadata && Object.keys(e.metadata).length ? JSON.stringify(e.metadata) : ''}</td>
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
