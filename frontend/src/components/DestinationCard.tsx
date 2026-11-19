import { Link } from 'react-router-dom';
import type { Area } from '../types';
import { AreaImage } from './AreaImage';

/** Image-led destination card (permit-required area) linking to a new application. */
export function DestinationCard({ area }: { area: Area }) {
  const location = [area.district, area.province].filter(Boolean).join(', ');
  const fee = Number(area.fee);
  return (
    <Link className="dest-card" to={`/tourist/applications/new?area=${area.id}`}>
      <div className="dest-media">
        <AreaImage code={area.code} photoUrl={area.photoUrl} alt={area.name} />
        {area.permitRequired && (
          <span className="live-chip">
            <span className="live-dot" /> Permit required
          </span>
        )}
      </div>
      <div className="dest-body">
        <span className="tag-chip">Up to {area.maximumDays} days</span>
        <h3>{area.name}</h3>
        <div className="dest-meta">
          <span className="meta-dot" aria-hidden />
          <span>
            {location || area.code} · <span className="accent-text">{fee > 0 ? `Fee ${fee.toFixed(2)}` : 'No fee'}</span>
          </span>
        </div>
      </div>
    </Link>
  );
}
