import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { notificationApi } from '../../api/endpoints';
import { NoticeItem } from '../../components/NotificationBell';
import { Alert, EmptyState, PageHeader, Pagination, Spinner } from '../../components/ui';
import { useAsync } from '../../hooks/useAsync';
import type { AppNotification } from '../../types';

export function NotificationsPage() {
  const navigate = useNavigate();
  const [page, setPage] = useState(0);
  const { data, error, loading, reload } = useAsync(() => notificationApi.list({ page, size: 20 }), [page]);

  const open = async (n: AppNotification) => {
    if (!n.read) await notificationApi.markRead(n.id).catch(() => undefined);
    if (n.link) navigate(n.link);
    else reload();
  };

  const readAll = async () => {
    await notificationApi.markAllRead().catch(() => undefined);
    reload();
  };

  return (
    <>
      <PageHeader
        title="Notifications"
        subtitle="Approvals, payments and permit expiry reminders"
        actions={data?.content.some((n) => !n.read) && <button className="btn btn-ghost" onClick={readAll}>Mark all read</button>}
      />
      <Alert>{error}</Alert>
      {loading && !data ? (
        <Spinner />
      ) : !data?.content.length ? (
        <EmptyState>You have no notifications yet.</EmptyState>
      ) : (
        <div className="notice-list">
          {data.content.map((n) => <NoticeItem key={n.id} n={n} onOpen={open} />)}
        </div>
      )}
      <Pagination page={data} onChange={setPage} />
    </>
  );
}
