import type { ReactNode } from 'react';
import type { Page } from '../types';
import { humanize } from '../utils/format';

const STATUS_TONE: Record<string, string> = {
  PENDING: 'warn',
  UNDER_REVIEW: 'info',
  APPROVED: 'ok',
  ACTIVE: 'ok',
  VALID: 'ok',
  REJECTED: 'bad',
  CANCELLED: 'bad',
  INVALID: 'bad',
  EXPIRED: 'muted',
  COMPLETED: 'muted',
};

export function StatusBadge({ status }: { status?: string }) {
  if (!status) return null;
  return <span className={`badge badge-${STATUS_TONE[status] ?? 'muted'}`}>{humanize(status)}</span>;
}

export function Spinner({ label = 'Loading…' }: { label?: string }) {
  return (
    <div className="spinner" role="status" aria-live="polite">
      <span className="spinner-dot" />
      {label}
    </div>
  );
}

export function Alert({ tone = 'error', children }: { tone?: 'error' | 'success' | 'info'; children: ReactNode }) {
  if (!children) return null;
  return (
    <div className={`alert alert-${tone}`} role={tone === 'error' ? 'alert' : 'status'}>
      {children}
    </div>
  );
}

export function PageHeader({ title, subtitle, actions }: { title: string; subtitle?: ReactNode; actions?: ReactNode }) {
  return (
    <header className="page-header">
      <div>
        <h1>{title}</h1>
        {subtitle && <p className="muted">{subtitle}</p>}
      </div>
      {actions && <div className="page-actions">{actions}</div>}
    </header>
  );
}

export function Card({ title, actions, children, className = '' }: {
  title?: ReactNode;
  actions?: ReactNode;
  children: ReactNode;
  className?: string;
}) {
  return (
    <section className={`card ${className}`}>
      {(title || actions) && (
        <div className="card-header">
          {title && <h2>{title}</h2>}
          {actions}
        </div>
      )}
      {children}
    </section>
  );
}

export function StatCard({ label, value, tone }: { label: string; value: number | string; tone?: string }) {
  return (
    <div className={`stat ${tone ? `stat-${tone}` : ''}`}>
      <div className="stat-value">{value}</div>
      <div className="stat-label">{label}</div>
    </div>
  );
}

export function EmptyState({ children }: { children: ReactNode }) {
  return <div className="empty">{children}</div>;
}

export function Pagination<T>({ page, onChange }: { page?: Page<T>; onChange: (page: number) => void }) {
  if (!page || page.totalPages <= 1) return null;
  return (
    <nav className="pagination" aria-label="Pagination">
      <button className="btn btn-ghost" disabled={page.page === 0} onClick={() => onChange(page.page - 1)}>
        ‹ Previous
      </button>
      <span className="muted">
        Page {page.page + 1} of {page.totalPages} · {page.totalElements} total
      </span>
      <button
        className="btn btn-ghost"
        disabled={page.page + 1 >= page.totalPages}
        onClick={() => onChange(page.page + 1)}
      >
        Next ›
      </button>
    </nav>
  );
}

export function Field({ label, error, children, hint }: {
  label: string;
  error?: string;
  hint?: string;
  children: ReactNode;
}) {
  return (
    <label className={`field ${error ? 'field-error' : ''}`}>
      <span className="field-label">{label}</span>
      {children}
      {hint && !error && <span className="field-hint">{hint}</span>}
      {error && <span className="field-message">{error}</span>}
    </label>
  );
}

export function DetailList({ items }: { items: [string, ReactNode][] }) {
  return (
    <dl className="details">
      {items.map(([k, v]) => (
        <div key={k}>
          <dt>{k}</dt>
          <dd>{v ?? '—'}</dd>
        </div>
      ))}
    </dl>
  );
}
