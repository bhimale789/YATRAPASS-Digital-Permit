import { useState } from 'react';
import { Link } from 'react-router-dom';
import { areaApi, touristApi } from '../../api/endpoints';
import { AreaImage } from '../../components/AreaImage';
import { Carousel } from '../../components/Carousel';
import { DestinationCard } from '../../components/DestinationCard';
import { Icon } from '../../components/Icon';
import { Alert, Spinner, StatusBadge } from '../../components/ui';
import { useAsync } from '../../hooks/useAsync';
import { formatDate } from '../../utils/format';
import { PermitCard } from './PermitCard';

type Tab = 'discover' | 'trips';

export function TouristDashboard() {
  const [tab, setTab] = useState<Tab>('discover');
  const { data, error, loading } = useAsync(
    () =>
      Promise.all([
        touristApi.profile(),
        areaApi.listActive(),
        touristApi.permits({ status: 'ACTIVE', size: 10 }),
        touristApi.applications({ size: 8 }),
      ]),
    [],
  );

  if (loading) return <Spinner />;
  if (error || !data) return <Alert>{error}</Alert>;
  const [profile, areas, active, applications] = data;
  const destinations = areas.filter((a) => a.permitRequired);
  const featured = destinations.find((a) => a.photoUrl) ?? destinations[0];

  return (
    <div className="t-page">
      <section className="promo">
        <div className="promo-art">
          <AreaImage code={featured?.code ?? 'welcome'} photoUrl={featured?.photoUrl} alt={featured?.name ?? ''} />
        </div>
        <div className="promo-text">
          {profile.complete ? (
            <>
              <h2>Plan your next adventure ▲</h2>
              <p>Apply for a permit to a restricted area and receive a digital permit with a secure QR code — no paperwork at the checkpoint.</p>
            </>
          ) : (
            <>
              <h2>Namaste, {profile.firstName}! Complete your traveller profile</h2>
              <p>Add your passport, nationality, date of birth and phone number once — then apply for permits in minutes.</p>
            </>
          )}
        </div>
        <Link className="btn btn-primary promo-cta" to={profile.complete ? '/tourist/applications/new' : '/tourist/profile'}>
          {profile.complete ? 'Apply for a permit' : 'Complete profile'}
        </Link>
      </section>

      <div className="t-tabs" role="tablist">
        <button role="tab" aria-selected={tab === 'discover'} className={tab === 'discover' ? 'active' : ''}
          onClick={() => setTab('discover')}>
          <Icon name="map" /> Discover
        </button>
        <button role="tab" aria-selected={tab === 'trips'} className={tab === 'trips' ? 'active' : ''}
          onClick={() => setTab('trips')}>
          <Icon name="ticket" /> My trips
        </button>
      </div>

      {tab === 'discover' ? (
        <>
          <section className="t-section">
            <div className="t-section-head">
              <h2>Popular destinations</h2>
              <Link to="/tourist/explore" className="see-all">See all</Link>
            </div>
            {destinations.length ? (
              <Carousel label="Popular destinations">
                {destinations.map((a) => <DestinationCard key={a.id} area={a} />)}
              </Carousel>
            ) : (
              <div className="t-empty">No destinations are open for applications right now.</div>
            )}
          </section>

          <section className="t-section">
            <div className="t-section-head">
              <h2>Your active permits</h2>
              <Link to="/tourist/permits" className="see-all">See all</Link>
            </div>
            {active.content.length ? (
              <Carousel label="Active permits">
                {active.content.map((p) => <PermitCard key={p.id} permit={p} />)}
              </Carousel>
            ) : (
              <div className="t-empty">
                No active permits yet. <Link to="/tourist/explore">Find a destination</Link> to get started.
              </div>
            )}
          </section>
        </>
      ) : (
        <section className="t-section">
          <div className="t-section-head">
            <h2>Recent applications</h2>
            <Link to="/tourist/applications" className="see-all">See all</Link>
          </div>
          {applications.content.length ? (
            <ul className="trip-list">
              {applications.content.map((a) => (
                <li key={a.id}>
                  <Link to={`/tourist/applications/${a.id}`} className="trip-row">
                    <span className="trip-thumb"><AreaImage code={a.area.code} photoUrl={a.area.photoUrl} alt={a.area.name} /></span>
                    <span className="trip-info">
                      <strong>{a.area.name}</strong>
                      <span className="muted small">
                        {a.applicationNumber} · {formatDate(a.startDate)} – {formatDate(a.endDate)}
                      </span>
                    </span>
                    <StatusBadge status={a.status} />
                  </Link>
                </li>
              ))}
            </ul>
          ) : (
            <div className="t-empty">You have not applied for any permits yet.</div>
          )}
        </section>
      )}
    </div>
  );
}
