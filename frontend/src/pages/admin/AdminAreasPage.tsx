import { useState, type FormEvent } from 'react';
import { ResponsiveTable } from '../../components/ResponsiveTable';
import { adminApi, type AreaInput } from '../../api/endpoints';
import { Alert, Card, EmptyState, Field, PageHeader, Pagination, Spinner } from '../../components/ui';
import { useAsync } from '../../hooks/useAsync';
import { useDebounce } from '../../hooks/useDebounce';
import { AreaImage } from '../../components/AreaImage';
import { AreaPhotoEditor } from './AreaPhotoEditor';
import { RoadMap } from '../../components/RoadMap';
import type { Area } from '../../types';
import { errorMessage, fieldErrors } from '../../utils/errors';

const BLANK: AreaInput = {
  name: '',
  code: '',
  description: '',
  district: '',
  province: '',
  permitRequired: true,
  maximumDays: 10,
  fee: 0,
  active: true,
  latitude: null,
  longitude: null,
};

export function AdminAreasPage() {
  const [q, setQ] = useState('');
  const [page, setPage] = useState(0);
  const debouncedQ = useDebounce(q);
  const { data, error, loading, reload } = useAsync(() => adminApi.areas({ q: debouncedQ, page, size: 20 }), [debouncedQ, page]);
  const [editing, setEditing] = useState<Area | 'new' | null>(null);
  const [form, setForm] = useState<AreaInput>(BLANK);
  const [formError, setFormError] = useState<string | null>(null);
  const [fields, setFields] = useState<Record<string, string>>({});
  const [busy, setBusy] = useState(false);
  const [notice, setNotice] = useState<string | null>(null);

  const startEdit = (area: Area | 'new') => {
    setEditing(area);
    setFormError(null);
    setFields({});
    setNotice(null);
    setForm(area === 'new' ? BLANK : {
      name: area.name, code: area.code, description: area.description ?? '', district: area.district ?? '',
      province: area.province ?? '', permitRequired: area.permitRequired, maximumDays: area.maximumDays,
      fee: area.fee, active: area.active, latitude: area.latitude ?? null, longitude: area.longitude ?? null,
    });
    window.scrollTo({ top: 0, behavior: 'smooth' });
  };

  const save = async (e: FormEvent) => {
    e.preventDefault();
    setBusy(true);
    setFormError(null);
    setFields({});
    try {
      if (editing === 'new') {
        // Keep the editor open so a photo can be added straight away.
        setEditing(await adminApi.createArea(form));
        setNotice('Area created. You can now add a destination photo below.');
      } else if (editing) {
        await adminApi.updateArea(editing.id, form);
        setEditing(null);
      }
      reload();
    } catch (err) {
      setFormError(errorMessage(err));
      setFields(fieldErrors(err));
    } finally {
      setBusy(false);
    }
  };

  const toggleActive = async (area: Area) => {
    try {
      await adminApi.setAreaActive(area.id, !area.active);
      reload();
    } catch (err) {
      alert(errorMessage(err));
    }
  };

  return (
    <>
      <PageHeader
        title="Permit Areas"
        subtitle="Configure permit-required areas. Rules and fees must come from the official authority."
        actions={<button className="btn btn-primary" onClick={() => startEdit('new')}>+ New area</button>}
      />

      {editing && (
        <Card title={editing === 'new' ? 'New area' : `Edit ${editing.code}`}>
          {notice && <Alert tone="success">{notice}</Alert>}
          {editing === 'new' ? (
            <p className="muted small photo-hint">Save the area first, then you can add a destination photo.</p>
          ) : (
            <AreaPhotoEditor key={editing.id} area={editing} onChange={(updated) => { setEditing(updated); reload(); }} />
          )}
          <form className="form" onSubmit={save}>
            <Alert>{formError}</Alert>
            <div className="grid-2">
              <Field label="Name" error={fields.name}>
                <input value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} maxLength={150} required />
              </Field>
              <Field label="Code" error={fields.code} hint="Letters, digits, '-' or '_'">
                <input value={form.code} onChange={(e) => setForm({ ...form, code: e.target.value })}
                  pattern="[A-Za-z0-9_\-]{2,30}" required />
              </Field>
              <Field label="District" error={fields.district}>
                <input value={form.district ?? ''} onChange={(e) => setForm({ ...form, district: e.target.value })} maxLength={100} />
              </Field>
              <Field label="Province" error={fields.province}>
                <input value={form.province ?? ''} onChange={(e) => setForm({ ...form, province: e.target.value })} maxLength={100} />
              </Field>
              <Field label="Maximum days" error={fields.maximumDays}>
                <input type="number" min={1} max={365} value={form.maximumDays}
                  onChange={(e) => setForm({ ...form, maximumDays: Number(e.target.value) })} required />
              </Field>
              <Field label="Fee" error={fields.fee}>
                <input type="number" min={0} step="0.01" value={form.fee}
                  onChange={(e) => setForm({ ...form, fee: Number(e.target.value) })} required />
              </Field>
            </div>
            <Field label="Description" error={fields.description}>
              <textarea value={form.description ?? ''} onChange={(e) => setForm({ ...form, description: e.target.value })}
                maxLength={2000} rows={2} />
            </Field>
            <div className="field location-field">
              <span className="field-label">Destination location (for tourist driving routes)</span>
              <span className="field-hint">
                {form.latitude != null && form.longitude != null
                  ? `Pin at ${Number(form.latitude).toFixed(5)}, ${Number(form.longitude).toFixed(5)} — click the map to move it.`
                  : 'Click the map to place the destination pin (e.g. the road head or main village).'}
              </span>
              <RoadMap
                conditions={[]}
                height={300}
                draft={form.latitude != null && form.longitude != null
                  ? { geometryType: 'POINT', coordinates: [{ lat: Number(form.latitude), lng: Number(form.longitude) }] }
                  : null}
                onMapClick={(p) => setForm({ ...form, latitude: p.lat, longitude: p.lng })}
              />
              {fields.locationComplete && <span className="field-message">{fields.locationComplete}</span>}
              {form.latitude != null && (
                <div className="row-actions draw-actions">
                  <button type="button" className="btn btn-ghost btn-sm"
                    onClick={() => setForm({ ...form, latitude: null, longitude: null })}>Clear pin</button>
                  <span className="muted small">Remember to click “Save area details”.</span>
                </div>
              )}
            </div>
            <div className="checks">
              <label><input type="checkbox" checked={form.permitRequired}
                onChange={(e) => setForm({ ...form, permitRequired: e.target.checked })} /> Permit required</label>
              <label><input type="checkbox" checked={form.active}
                onChange={(e) => setForm({ ...form, active: e.target.checked })} /> Active (accepting applications)</label>
            </div>
            <div className="form-actions">
              <button type="button" className="btn btn-ghost" onClick={() => setEditing(null)}>
                {editing === 'new' ? 'Cancel' : 'Close'}
              </button>
              <button className="btn btn-primary" disabled={busy}>{busy ? 'Saving…' : 'Save area details'}</button>
            </div>
          </form>
        </Card>
      )}

      <Card>
        <div className="toolbar">
          <input type="search" placeholder="Name, code or district…" value={q}
            onChange={(e) => { setQ(e.target.value); setPage(0); }} maxLength={100} />
        </div>
        <Alert>{error}</Alert>
        {loading && !data ? (
          <Spinner />
        ) : !data?.content.length ? (
          <EmptyState>No areas configured.</EmptyState>
        ) : (
          <ResponsiveTable>
            <table>
              <thead>
                <tr>
                  <th>Photo</th>
                  <th>Code</th>
                  <th>Name</th>
                  <th>District / Province</th>
                  <th>Max days</th>
                  <th>Fee</th>
                  <th>Status</th>
                  <th />
                </tr>
              </thead>
              <tbody>
                {data.content.map((a) => (
                  <tr key={a.id}>
                    <td>
                      <button className="area-thumb" onClick={() => startEdit(a)}
                        title={a.photoUrl ? 'Change photo' : 'Add photo'} aria-label={`Edit photo for ${a.name}`}>
                        <AreaImage code={a.code} photoUrl={a.photoUrl} alt={a.name} />
                        {!a.photoUrl && <span className="area-thumb-add">+ Photo</span>}
                      </button>
                    </td>
                    <td className="mono">{a.code}</td>
                    <td>{a.name}</td>
                    <td>{[a.district, a.province].filter(Boolean).join(' / ') || '—'}</td>
                    <td>{a.maximumDays}</td>
                    <td>{Number(a.fee).toFixed(2)}</td>
                    <td>{a.active ? <span className="badge badge-ok">Active</span> : <span className="badge badge-muted">Inactive</span>}</td>
                    <td className="row-actions">
                      <button className="btn btn-ghost btn-sm" onClick={() => startEdit(a)}>Edit</button>
                      <button className="btn btn-ghost btn-sm" onClick={() => toggleActive(a)}>
                        {a.active ? 'Deactivate' : 'Activate'}
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </ResponsiveTable>
        )}
        <Pagination page={data} onChange={setPage} />
      </Card>
    </>
  );
}
