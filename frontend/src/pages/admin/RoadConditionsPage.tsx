import { useMemo, useState, type FormEvent } from 'react';
import { areaApi, roadApi, type RoadConditionInput } from '../../api/endpoints';
import { ROAD_STATUS_COLOR, RoadLegend, RoadMap } from '../../components/RoadMap';
import { Alert, Card, EmptyState, Field, PageHeader, Spinner } from '../../components/ui';
import { useAsync } from '../../hooks/useAsync';
import type { Coordinate, GeometryType, RoadCondition, RoadStatus } from '../../types';
import { errorMessage } from '../../utils/errors';
import { formatDateTime, humanize } from '../../utils/format';

const STATUSES: RoadStatus[] = ['OPEN', 'CAUTION', 'PARTIALLY_BLOCKED', 'CLOSED'];

interface Draft {
  id?: number;
  title: string;
  description: string;
  status: RoadStatus;
  geometryType: GeometryType;
  coordinates: Coordinate[];
  areaId: string;
  active: boolean;
}

const NEW_DRAFT: Draft = {
  title: '', description: '', status: 'CAUTION', geometryType: 'LINE', coordinates: [], areaId: '', active: true,
};

function toDraft(c: RoadCondition): Draft {
  return {
    id: c.id, title: c.title, description: c.description ?? '', status: c.status, geometryType: c.geometryType,
    coordinates: c.coordinates, areaId: c.area ? String(c.area.id) : '', active: c.active,
  };
}

export function RoadConditionsPage() {
  const [showInactive, setShowInactive] = useState(true);
  const [statusFilter, setStatusFilter] = useState<RoadStatus | ''>('');
  const list = useAsync(
    () => roadApi.adminSearch({ size: 100, status: statusFilter || undefined, active: showInactive ? undefined : true }),
    [showInactive, statusFilter],
  );
  const areas = useAsync(() => areaApi.listActive(), []);
  const [draft, setDraft] = useState<Draft | null>(null);
  const [selected, setSelected] = useState<number | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const conditions = useMemo(
    () => (list.data?.content ?? []).filter((c) => c.id !== draft?.id),
    [list.data, draft?.id],
  );

  const edit = (c: RoadCondition) => {
    setSelected(c.id);
    setDraft(toDraft(c));
    setError(null);
    setNotice(null);
  };

  const startNew = () => {
    setSelected(null);
    setDraft({ ...NEW_DRAFT });
    setError(null);
    setNotice(null);
  };

  const addPoint = (p: Coordinate) => {
    setDraft((d) => {
      if (!d) return d;
      if (d.geometryType === 'POINT') return { ...d, coordinates: [p] };
      if (d.coordinates.length >= 500) return d;
      return { ...d, coordinates: [...d.coordinates, p] };
    });
  };

  const geometryError = !draft ? null
    : draft.geometryType === 'POINT' && draft.coordinates.length !== 1 ? 'Click the map to place the point.'
      : draft.geometryType === 'LINE' && draft.coordinates.length < 2 ? 'Click the map at least twice to draw the road stretch.'
        : null;

  const save = async (e: FormEvent) => {
    e.preventDefault();
    if (!draft || geometryError) return;
    setBusy(true);
    setError(null);
    const body: RoadConditionInput = {
      title: draft.title, description: draft.description || undefined, status: draft.status,
      geometryType: draft.geometryType, coordinates: draft.coordinates,
      areaId: draft.areaId ? Number(draft.areaId) : null, active: draft.active,
    };
    try {
      const saved = draft.id ? await roadApi.update(draft.id, body) : await roadApi.create(body);
      setNotice(`“${saved.title}” saved — ${saved.active ? 'visible to tourists and officers' : 'hidden from public maps'}.`);
      setDraft(null);
      setSelected(saved.id);
      list.reload();
    } catch (err) {
      setError(errorMessage(err));
    } finally {
      setBusy(false);
    }
  };

  const remove = async () => {
    if (!draft?.id || !confirm(`Delete “${draft.title}” permanently? Tip: untick “Show on public maps” to keep it in history instead.`)) return;
    setBusy(true);
    try {
      await roadApi.remove(draft.id);
      setNotice('Road condition deleted.');
      setDraft(null);
      setSelected(null);
      list.reload();
    } catch (err) {
      setError(errorMessage(err));
    } finally {
      setBusy(false);
    }
  };

  return (
    <>
      <PageHeader
        title="Road Conditions"
        subtitle="Mark road segments and locations on the map. Tourists see them when applying; officers on their dashboard."
        actions={<button className="btn btn-primary" onClick={startNew}>+ New condition</button>}
      />
      {notice && <Alert tone="success">{notice}</Alert>}

      <div className="road-admin">
        <Card className="road-admin-map">
          {draft && (
            <div className="draw-hint" role="status">
              {draft.geometryType === 'LINE'
                ? `Drawing road segment — click along the road (${draft.coordinates.length} point${draft.coordinates.length === 1 ? '' : 's'}).`
                : draft.coordinates.length ? 'Point placed — click elsewhere to move it.' : 'Click the map to place the point.'}
            </div>
          )}
          <RoadMap
            conditions={conditions}
            height={560}
            selectedId={draft ? null : selected}
            onSelect={(id) => {
              const c = list.data?.content.find((x) => x.id === id);
              if (c && !draft) edit(c);
            }}
            draft={draft}
            onMapClick={draft ? addPoint : undefined}
            fitKey={list.data ? 'loaded' : undefined}
          />
          <RoadLegend />
          <p className="muted small">Dashed / faded = hidden from public maps.</p>
        </Card>

        <div className="road-admin-side">
          {draft ? (
            <Card title={draft.id ? 'Edit road condition' : 'New road condition'}>
              <form className="form" onSubmit={save}>
                <Alert>{error}</Alert>
                <div className="segmented" role="radiogroup" aria-label="Shape">
                  {(['LINE', 'POINT'] as GeometryType[]).map((g) => (
                    <button key={g} type="button" role="radio" aria-checked={draft.geometryType === g}
                      className={draft.geometryType === g ? 'active' : ''}
                      onClick={() => setDraft({
                        ...draft, geometryType: g,
                        coordinates: g === 'POINT' ? draft.coordinates.slice(-1) : draft.coordinates,
                      })}>
                      {g === 'LINE' ? '〰 Road segment' : '● Point'}
                    </button>
                  ))}
                </div>
                <div className="row-actions draw-actions">
                  <button type="button" className="btn btn-ghost btn-sm" disabled={!draft.coordinates.length}
                    onClick={() => setDraft({ ...draft, coordinates: draft.coordinates.slice(0, -1) })}>↶ Undo point</button>
                  <button type="button" className="btn btn-ghost btn-sm" disabled={!draft.coordinates.length}
                    onClick={() => setDraft({ ...draft, coordinates: [] })}>Clear shape</button>
                </div>
                {geometryError && <p className="field-hint">{geometryError}</p>}

                <Field label="Title">
                  <input value={draft.title} onChange={(e) => setDraft({ ...draft, title: e.target.value })}
                    maxLength={150} required placeholder="e.g. Landslide near Tatopani" />
                </Field>
                <Field label="Status">
                  <div className="status-pick">
                    {STATUSES.map((s) => (
                      <label key={s} className={draft.status === s ? 'active' : ''}>
                        <input type="radio" name="status" value={s} checked={draft.status === s}
                          onChange={() => setDraft({ ...draft, status: s })} />
                        <span className="road-swatch" style={{ background: ROAD_STATUS_COLOR[s] }} />
                        {humanize(s)}
                      </label>
                    ))}
                  </div>
                </Field>
                <Field label="Affected destination (optional)">
                  <select value={draft.areaId} onChange={(e) => setDraft({ ...draft, areaId: e.target.value })}>
                    <option value="">General — not tied to one destination</option>
                    {areas.data?.map((a) => <option key={a.id} value={a.id}>{a.name}</option>)}
                  </select>
                </Field>
                <Field label="Details for travellers">
                  <textarea value={draft.description} onChange={(e) => setDraft({ ...draft, description: e.target.value })}
                    maxLength={1000} rows={3} placeholder="e.g. One lane open, expect 1–2 hour delays" />
                </Field>
                <label className="check">
                  <input type="checkbox" checked={draft.active} onChange={(e) => setDraft({ ...draft, active: e.target.checked })} />
                  Show on public maps (tourists &amp; officers)
                </label>
                <div className="form-actions">
                  {draft.id && <button type="button" className="btn btn-danger-ghost" onClick={remove} disabled={busy}>Delete</button>}
                  <button type="button" className="btn btn-ghost" onClick={() => setDraft(null)} disabled={busy}>Cancel</button>
                  <button className="btn btn-primary" disabled={busy || !!geometryError}>{busy ? 'Saving…' : 'Save'}</button>
                </div>
              </form>
            </Card>
          ) : (
            <Card title="Reported conditions" actions={
              <div className="row-actions">
                <select value={statusFilter} onChange={(e) => setStatusFilter(e.target.value as RoadStatus | '')} aria-label="Status filter">
                  <option value="">All statuses</option>
                  {STATUSES.map((s) => <option key={s} value={s}>{humanize(s)}</option>)}
                </select>
              </div>
            }>
              <label className="check road-inactive-toggle">
                <input type="checkbox" checked={showInactive} onChange={(e) => setShowInactive(e.target.checked)} />
                Include hidden / resolved
              </label>
              <Alert>{list.error}</Alert>
              {list.loading && !list.data ? <Spinner /> : !list.data?.content.length ? (
                <EmptyState>No road conditions yet. Click “+ New condition” to add one.</EmptyState>
              ) : (
                <ul className="road-alerts road-admin-list">
                  {list.data.content.map((c) => (
                    <li key={c.id}>
                      <button className={selected === c.id ? 'active' : ''} onClick={() => edit(c)}>
                        <span className="road-swatch" style={{ background: ROAD_STATUS_COLOR[c.status] }} />
                        <span className="road-alert-text">
                          <strong>{c.title}{!c.active && <span className="badge badge-muted road-hidden">Hidden</span>}</strong>
                          <span className="muted small">
                            {humanize(c.status)} · {c.geometryType === 'LINE' ? 'Road segment' : 'Point'}
                            {c.area ? ` · ${c.area.name}` : ''}
                          </span>
                          <span className="muted small">Updated {formatDateTime(c.updatedAt)}{c.updatedBy ? ` by ${c.updatedBy}` : ''}</span>
                        </span>
                      </button>
                    </li>
                  ))}
                </ul>
              )}
            </Card>
          )}
        </div>
      </div>
    </>
  );
}
