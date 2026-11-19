import { Link } from 'react-router-dom';
import { officerApi } from '../../api/endpoints';
import { AreaArt } from '../../components/AreaArt';
import { RoadConditionsCard } from '../../components/RoadConditionsCard';
import { Alert, EmptyState, Spinner, StatCard, StatusBadge } from '../../components/ui';
import { useAuth } from '../../hooks/useAuth';
import { useAsync } from '../../hooks/useAsync';
import { formatDateTime, humanize } from '../../utils/format';

export function OfficerDashboard() {
  const { user } = useAuth();
  const { data, error, loading, reload } = useAsync(() => officerApi.dashboard(), []);

  if (loading && !data) return <Spinner />;
  if (error || !data) return <Alert>{error}</Alert>;
  const s = data.stats;

  return (
    <div className="t-page">
      <div className="t-section-head">
        <h1>Namaste, officer</h1>
        <button className="btn btn-ghost btn-sm" onClick={reload}>↻ Refresh</button>
      </div>

      <section className="promo">
        <div className="promo-art"><AreaArt seed={user?.email ?? 'checkpoint'} /></div>
        <div className="promo-text">
          <h2>Ready to check permits</h2>
          <p>
            {s.permitsValidToday} permit{s.permitsValidToday === 1 ? ' is' : 's are'} valid today. Open the scanner and
            point your camera at the tourist's QR code — the result appears instantly.
          </p>
        </div>
        <Link className="btn btn-primary promo-cta" to="/admin/scanner">Scan a permit</Link>
      </section>

      <div className="stats">
        <StatCard label="My scans today" value={s.scansToday} />
        <StatCard label="Valid today" value={s.validToday} tone="ok" />
        <StatCard label="Invalid today" value={s.invalidToday} tone="bad" />
        <StatCard label="Permits valid today" value={s.permitsValidToday} tone="info" />
      </div>

      <RoadConditionsCard title="Road conditions & closures" />

      <section className="t-section">
        <div className="t-section-head">
          <h2>My recent scans</h2>
          <span className="muted">{s.totalScans} total</span>
        </div>
        {!data.recentScans.length ? (
          <EmptyState>You have not scanned any permits yet.</EmptyState>
        ) : (
          <ul className="trip-list">
            {data.recentScans.map((v) => (
              <li key={v.id} className="trip-row scan-row">
                <span className={`scan-dot scan-${v.result.toLowerCase()}`} aria-hidden>{v.result === 'VALID' ? '✓' : '✕'}</span>
                <span className="trip-info">
                  <strong>{v.permitNumber ?? 'Unknown code'}</strong>
                  <span className="muted small">
                    {formatDateTime(v.verifiedAt)}
                    {v.area && ` · ${v.area}`}
                    {v.failureReason && ` · ${humanize(v.failureReason)}`}
                  </span>
                </span>
                <StatusBadge status={v.result} />
              </li>
            ))}
          </ul>
        )}
      </section>
    </div>
  );
}
