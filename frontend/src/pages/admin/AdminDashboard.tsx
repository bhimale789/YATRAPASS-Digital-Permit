import { useState, type FormEvent } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { adminApi } from '../../api/endpoints';
import { RoadConditionsCard } from '../../components/RoadConditionsCard';
import { Alert, Card, EmptyState, PageHeader, Spinner, StatCard, StatusBadge } from '../../components/ui';
import { useAsync } from '../../hooks/useAsync';
import { formatDate, formatDateTime, humanize, formatMoney } from '../../utils/format';

export function AdminDashboard() {
  const navigate = useNavigate();
  const { data, error, loading, reload } = useAsync(() => adminApi.dashboard(), []);
  const [query, setQuery] = useState('');

  const search = (e: FormEvent) => {
    e.preventDefault();
    if (query.trim()) navigate(`/admin/permits?q=${encodeURIComponent(query.trim())}`);
  };

  if (loading && !data) return <Spinner />;
  if (error || !data) return <Alert>{error}</Alert>;
  const s = data.stats;
  const maxArea = Math.max(1, ...data.permitsByArea.map((a) => a.totalPermits));

  return (
    <>
      <PageHeader
        title="Admin Dashboard"
        actions={
          <>
            <button className="btn btn-ghost" onClick={reload}>
              ↻ Refresh
            </button>
            <Link className="btn btn-primary" to="/admin/scanner">
              Open QR scanner
            </Link>
          </>
        }
      />

      <form className="search-bar" onSubmit={search} role="search">
        <input
          type="search"
          placeholder="Search permit no., application no., tourist name, passport or area…"
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          maxLength={100}
        />
        <button className="btn btn-primary">Search permits</button>
        <button
          type="button"
          className="btn btn-ghost"
          onClick={() => query.trim() && navigate(`/admin/applications?q=${encodeURIComponent(query.trim())}`)}
        >
          Search applications
        </button>
      </form>
      <div className="stats">
        <StatCard label="Total tourists" value={s.totalTourists} />
        <StatCard label="Pending applications" value={s.pendingApplications} tone="warn" />
        <StatCard label="Under review" value={s.underReviewApplications} tone="info" />
        <StatCard label="Active permits" value={s.activePermits} tone="ok" />
        <StatCard label="Expired permits" value={s.expiredPermits} tone="muted" />
        <StatCard label="Cancelled permits" value={s.cancelledPermits} tone="bad" />
        <StatCard label="QR scans today" value={s.verificationsToday} />
        <StatCard label="Valid scans today" value={s.validScansToday} tone="ok" />
        <StatCard label="Invalid scans today" value={s.invalidScansToday} tone="bad" />
        {data.fees && (
          <>
            <StatCard label="Fees collected (this month)" value={formatMoney(data.fees.collectedThisMonth)} tone="ok" />
            <StatCard label="Fees collected (total)" value={formatMoney(data.fees.collectedTotal)} />
            <StatCard label="Unpaid open applications" value={data.fees.unpaidOpenApplications}
              tone={data.fees.unpaidOpenApplications ? 'warn' : 'muted'} />
          </>
        )}
      </div>

      <RoadConditionsCard actions={<Link to="/admin/roads">Manage →</Link>} />

      <div className="grid-2 align-start">
        <Card title="Recent applications" actions={<Link to="/admin/applications">View all →</Link>}>
          {!data.recentApplications.length ? (
            <EmptyState>No applications yet.</EmptyState>
          ) : (
            <ul className="activity">
              {data.recentApplications.map((a) => (
                <li key={a.id}>
                  <Link to={`/admin/applications/${a.id}`}>{a.applicationNumber}</Link>
                  <span>
                    {a.tourist?.fullName} · {a.area.name}
                  </span>
                  <StatusBadge status={a.status} />
                </li>
              ))}
            </ul>
          )}
        </Card>

        <Card title="Recent QR verifications" actions={<Link to="/admin/verifications">View all →</Link>}>
          {!data.recentVerifications.length ? (
            <EmptyState>No scans yet.</EmptyState>
          ) : (
            <ul className="activity">
              {data.recentVerifications.map((v) => (
                <li key={v.id}>
                  <span>{formatDateTime(v.verifiedAt)}</span>
                  <span>
                    {v.permitNumber ?? 'Unknown code'} {v.failureReason && `· ${humanize(v.failureReason)}`}
                  </span>
                  <StatusBadge status={v.result} />
                </li>
              ))}
            </ul>
          )}
        </Card>

        <Card title="Permits expiring within 7 days" actions={<Link to="/admin/permits?status=ACTIVE">Active →</Link>}>
          {!data.expiringPermits.length ? (
            <EmptyState>Nothing expiring soon.</EmptyState>
          ) : (
            <ul className="activity">
              {data.expiringPermits.map((p) => (
                <li key={p.id}>
                  <Link to={`/admin/permits/${p.id}`}>{p.permitNumber}</Link>
                  <span>
                    {p.tourist?.fullName} · {p.area.name}
                  </span>
                  <span className="muted">until {formatDate(p.validUntil)}</span>
                </li>
              ))}
            </ul>
          )}
        </Card>

        <Card title="Permits by area">
          {!data.permitsByArea.length ? (
            <EmptyState>No permits issued yet.</EmptyState>
          ) : (
            <ul className="bars">
              {data.permitsByArea.map((a) => (
                <li key={a.areaId}>
                  <div className="bar-label">
                    <span>{a.areaName}</span>
                    <span className="muted">
                      {a.activePermits} active / {a.totalPermits} total
                    </span>
                  </div>
                  <div className="bar-track">
                    <div className="bar-fill" style={{ width: `${(a.totalPermits / maxArea) * 100}%` }} />
                    <div className="bar-fill bar-active" style={{ width: `${(a.activePermits / maxArea) * 100}%` }} />
                  </div>
                </li>
              ))}
            </ul>
          )}
        </Card>
      </div>
    </>
  );
}
