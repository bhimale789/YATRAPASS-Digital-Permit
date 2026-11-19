import { useMemo, useState, type ReactNode } from 'react';
import { roadApi } from '../api/endpoints';
import { useAsync } from '../hooks/useAsync';
import type { RoadStatus } from '../types';
import { formatDateTime, humanize } from '../utils/format';
import { ROAD_STATUS_COLOR, RoadLegend, RoadMap } from './RoadMap';
import { Alert, Card, Spinner } from './ui';

const SEVERITY: Record<RoadStatus, number> = { CLOSED: 0, PARTIALLY_BLOCKED: 1, CAUTION: 2, OPEN: 3 };

interface Props {
  title?: string;
  /** Limit to one destination (its conditions plus general ones). */
  areaId?: number;
  height?: number;
  actions?: ReactNode;
  /** Text shown when there are no conditions. */
  emptyText?: string;
}

/** Map + alert list of current road conditions, for dashboards and the application form. */
export function RoadConditionsCard({ title = 'Road conditions', areaId, height = 360, actions, emptyText }: Props) {
  const { data, error, loading } = useAsync(() => roadApi.active(areaId), [areaId]);
  const [selected, setSelected] = useState<number | null>(null);
  const alerts = useMemo(
    () => [...(data ?? [])].filter((c) => c.status !== 'OPEN').sort((a, b) => SEVERITY[a.status] - SEVERITY[b.status]),
    [data],
  );

  return (
    <Card title={title} actions={actions} className="road-card">
      <Alert>{error}</Alert>
      {loading && !data ? (
        <Spinner label="Loading map…" />
      ) : (
        <>
          <RoadMap conditions={data ?? []} height={height} selectedId={selected} onSelect={setSelected} fitKey={areaId ?? 'all'} />
          <RoadLegend />
          {data && data.length === 0 ? (
            <p className="muted small road-empty">{emptyText ?? 'No road issues reported. Roads are shown as normal.'}</p>
          ) : alerts.length > 0 ? (
            <ul className="road-alerts">
              {alerts.slice(0, 6).map((c) => (
                <li key={c.id}>
                  <button className={selected === c.id ? 'active' : ''} onClick={() => setSelected(c.id)}>
                    <span className="road-swatch" style={{ background: ROAD_STATUS_COLOR[c.status] }} />
                    <span className="road-alert-text">
                      <strong>{c.title}</strong>
                      <span className="muted small">
                        {humanize(c.status)}{c.area ? ` · ${c.area.name}` : ''} · {formatDateTime(c.updatedAt)}
                      </span>
                    </span>
                  </button>
                </li>
              ))}
            </ul>
          ) : (
            <p className="muted small road-empty">All reported roads are open.</p>
          )}
        </>
      )}
    </Card>
  );
}
