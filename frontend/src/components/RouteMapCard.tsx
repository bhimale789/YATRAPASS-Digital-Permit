import { useMemo, useState } from 'react';
import { areaApi, roadApi } from '../api/endpoints';
import { useAsync } from '../hooks/useAsync';
import type { Area, Coordinate, DrivingRoute, RoadCondition, RoadStatus } from '../types';
import { formatDateTime, humanize } from '../utils/format';
import { ROAD_STATUS_COLOR, RoadLegend, RoadMap } from './RoadMap';
import { Alert, Card, Spinner } from './ui';

const SEVERITY: Record<RoadStatus, number> = { CLOSED: 0, PARTIALLY_BLOCKED: 1, CAUTION: 2, OPEN: 3 };
/** Conditions within this distance of the route count as "on the route". */
const NEAR_ROUTE_KM = 3;

function km(a: Coordinate, b: Coordinate): number {
  const r = 6371;
  const dLat = ((b.lat - a.lat) * Math.PI) / 180;
  const dLng = ((b.lng - a.lng) * Math.PI) / 180;
  const h = Math.sin(dLat / 2) ** 2 + Math.cos((a.lat * Math.PI) / 180) * Math.cos((b.lat * Math.PI) / 180) * Math.sin(dLng / 2) ** 2;
  return 2 * r * Math.asin(Math.sqrt(h));
}

function isNearRoute(c: RoadCondition, route: DrivingRoute): boolean {
  return c.coordinates.some((p) => route.coordinates.some((q) => km(p, q) <= NEAR_ROUTE_KM));
}

function formatDistance(meters: number): string {
  return meters >= 10_000 ? `${Math.round(meters / 1000)} km` : `${(meters / 1000).toFixed(1)} km`;
}

function formatDuration(seconds: number): string {
  const h = Math.floor(seconds / 3600);
  const m = Math.round((seconds % 3600) / 60);
  return h > 0 ? `${h} h ${m} min` : `${m} min`;
}

/** Driving route to the selected destination with road conditions along it. */
export function RouteMapCard({ area }: { area: Area }) {
  const [origin, setOrigin] = useState<Coordinate | null>(null);
  const [locating, setLocating] = useState(false);
  const [locateError, setLocateError] = useState<string | null>(null);
  const hasLocation = area.latitude != null && area.longitude != null;

  const route = useAsync(
    () => (hasLocation ? areaApi.route(area.id, origin) : Promise.resolve(null)),
    [area.id, hasLocation, origin?.lat, origin?.lng],
  );
  const conditions = useAsync(() => roadApi.active(), []);

  const onRoute = useMemo(() => {
    const all = conditions.data ?? [];
    return all
      .filter((c) => c.status !== 'OPEN')
      .filter((c) => c.area?.id === area.id || (route.data ? isNearRoute(c, route.data) : false))
      .sort((a, b) => SEVERITY[a.status] - SEVERITY[b.status]);
  }, [conditions.data, route.data, area.id]);

  const useMyLocation = () => {
    setLocateError(null);
    if (!navigator.geolocation) {
      setLocateError('Location is not available in this browser.');
      return;
    }
    setLocating(true);
    navigator.geolocation.getCurrentPosition(
      (pos) => {
        setOrigin({ lat: +pos.coords.latitude.toFixed(5), lng: +pos.coords.longitude.toFixed(5) });
        setLocating(false);
      },
      () => {
        setLocateError('Could not get your location. Allow location access or start from the default.');
        setLocating(false);
      },
      { timeout: 10_000, maximumAge: 300_000 },
    );
  };

  const r = route.data;

  return (
    <Card title={`Route to ${area.name}`} className="road-card route-card" actions={
      hasLocation && (origin ? (
        <button type="button" className="btn btn-ghost btn-sm" onClick={() => setOrigin(null)}>Start from default</button>
      ) : (
        <button type="button" className="btn btn-ghost btn-sm" onClick={useMyLocation} disabled={locating}>
          {locating ? 'Locating…' : '◎ Use my location'}
        </button>
      ))
    }>
      {!hasLocation && (
        <Alert tone="info">A driving route isn't available yet — this destination's location hasn't been set. Road conditions are shown below.</Alert>
      )}
      <Alert>{locateError}</Alert>
      {hasLocation && route.error && <Alert>{route.error}</Alert>}

      {r && (
        <div className="route-summary">
          <span className="route-ends"><strong>{r.from.label}</strong> → <strong>{r.to.label}</strong></span>
          <span className="route-stat">{formatDistance(r.distanceMeters)}</span>
          <span className="route-stat">≈ {formatDuration(r.durationSeconds)} by road</span>
        </div>
      )}

      {(route.loading && hasLocation && !r) || (conditions.loading && !conditions.data) ? (
        <Spinner label="Finding the route…" />
      ) : (
        <RoadMap conditions={conditions.data ?? []} route={r} height={360} fitKey="route" />
      )}

      <div className="route-legend-row">
        <span className="route-key"><span className="route-line" /> Driving route</span>
        <RoadLegend />
      </div>

      <h3 className="route-subhead">Road conditions on this route</h3>
      {onRoute.length === 0 ? (
        <p className="muted small road-empty">No reported problems{r ? ' along this route' : ' for this destination'}.</p>
      ) : (
        <ul className="road-alerts">
          {onRoute.map((c) => (
            <li key={c.id}>
              <div className="road-alert-static">
                <span className="road-swatch" style={{ background: ROAD_STATUS_COLOR[c.status] }} />
                <span className="road-alert-text">
                  <strong>{c.title}</strong>
                  <span className="muted small">{humanize(c.status)} · updated {formatDateTime(c.updatedAt)}</span>
                  {c.description && <span className="small">{c.description}</span>}
                </span>
              </div>
            </li>
          ))}
        </ul>
      )}

      <p className="muted small route-note">
        Estimated driving route; trekking sections beyond the road end are not shown.
        {origin && ' Your location is used only to calculate this route and is not stored.'}
        {r && <> {r.attribution}.</>}
      </p>
    </Card>
  );
}
