import { useCallback, useEffect, useRef, useState } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { notificationApi } from '../api/endpoints';
import type { AppNotification } from '../types';
import { Icon } from './Icon';
import { formatDateTime } from '../utils/format';

const POLL_MS = 60_000;

/** Header bell with unread count and a dropdown of the latest notifications. */
export function NotificationBell() {
  const navigate = useNavigate();
  const location = useLocation();
  const [count, setCount] = useState(0);
  const [open, setOpen] = useState(false);
  const [items, setItems] = useState<AppNotification[] | null>(null);
  const ref = useRef<HTMLDivElement>(null);

  const refreshCount = useCallback(() => {
    notificationApi.unreadCount().then(setCount).catch(() => undefined);
  }, []);

  // Poll while the tab is visible, and on every navigation.
  useEffect(() => {
    refreshCount();
    const timer = window.setInterval(() => document.visibilityState === 'visible' && refreshCount(), POLL_MS);
    return () => window.clearInterval(timer);
  }, [refreshCount, location.pathname]);

  useEffect(() => setOpen(false), [location.pathname]);

  useEffect(() => {
    if (!open) return;
    notificationApi.list({ size: 8 }).then((p) => setItems(p.content)).catch(() => setItems([]));
    const click = (e: MouseEvent) => !ref.current?.contains(e.target as Node) && setOpen(false);
    const key = (e: KeyboardEvent) => e.key === 'Escape' && setOpen(false);
    document.addEventListener('mousedown', click);
    document.addEventListener('keydown', key);
    return () => {
      document.removeEventListener('mousedown', click);
      document.removeEventListener('keydown', key);
    };
  }, [open]);

  const openItem = async (n: AppNotification) => {
    if (!n.read) {
      await notificationApi.markRead(n.id).catch(() => undefined);
      refreshCount();
    }
    setOpen(false);
    if (n.link) navigate(n.link);
  };

  const readAll = async () => {
    await notificationApi.markAllRead().catch(() => undefined);
    setItems((list) => list?.map((n) => ({ ...n, read: true })) ?? null);
    setCount(0);
  };

  return (
    <div className="t-notify" ref={ref}>
      <button className="t-bell" aria-label={`Notifications${count ? ` (${count} unread)` : ''}`}
        aria-haspopup="dialog" aria-expanded={open} onClick={() => setOpen((o) => !o)}>
        <Icon name="bell" />
        {count > 0 && <span className="t-bell-count">{count > 99 ? '99+' : count}</span>}
      </button>
      {open && (
        <div className="t-notify-panel" role="dialog" aria-label="Notifications">
          <div className="t-notify-head">
            <strong>Notifications</strong>
            {count > 0 && <button className="link-btn" onClick={readAll}>Mark all read</button>}
          </div>
          {items === null ? (
            <p className="muted center small">Loading…</p>
          ) : items.length === 0 ? (
            <p className="muted center small">No notifications yet.</p>
          ) : (
            items.map((n) => <NoticeItem key={n.id} n={n} onOpen={openItem} />)
          )}
          <Link className="notice center small" to="/tourist/notifications">See all notifications</Link>
        </div>
      )}
    </div>
  );
}

export function NoticeItem({ n, onOpen }: { n: AppNotification; onOpen: (n: AppNotification) => void }) {
  return (
    <button className={`notice ${n.read ? '' : 'unread'}`} onClick={() => onOpen(n)}>
      <div className="notice-title">{n.title}</div>
      <div className="notice-body">{n.body}</div>
      <div className="notice-time">{formatDateTime(n.createdAt)}</div>
    </button>
  );
}
