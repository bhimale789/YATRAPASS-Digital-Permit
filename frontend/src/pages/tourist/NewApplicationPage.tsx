import { useMemo, useState, type FormEvent } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import { areaApi, touristApi } from '../../api/endpoints';
import { RoadConditionsCard } from '../../components/RoadConditionsCard';
import { RouteMapCard } from '../../components/RouteMapCard';
import { Alert, Card, Field, PageHeader, Spinner } from '../../components/ui';
import { useAsync } from '../../hooks/useAsync';
import { errorMessage, fieldErrors } from '../../utils/errors';
import { todayIso } from '../../utils/format';

function inclusiveDays(start: string, end: string): number {
  const ms = Date.parse(end) - Date.parse(start);
  return Number.isNaN(ms) ? 0 : Math.round(ms / 86_400_000) + 1;
}

export function NewApplicationPage() {
  const navigate = useNavigate();
  const [params] = useSearchParams();
  const { data, error: loadError, loading } = useAsync(
    () => Promise.all([areaApi.listActive(), touristApi.profile()]),
    [],
  );
  const [form, setForm] = useState({ areaId: params.get('area') ?? '', purpose: '', startDate: '', endDate: '' });
  const [error, setError] = useState<string | null>(null);
  const [fields, setFields] = useState<Record<string, string>>({});
  const [busy, setBusy] = useState(false);

  const areas = useMemo(() => (data?.[0] ?? []).filter((a) => a.permitRequired), [data]);
  const selected = areas.find((a) => String(a.id) === form.areaId);
  const days = form.startDate && form.endDate ? inclusiveDays(form.startDate, form.endDate) : 0;

  if (loading) return <Spinner />;
  if (loadError || !data) return <Alert>{loadError}</Alert>;
  const profile = data[1];

  const clientError =
    form.startDate && form.endDate && form.endDate < form.startDate
      ? 'End date cannot be before start date'
      : selected && days > selected.maximumDays
        ? `This area allows at most ${selected.maximumDays} days`
        : null;

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    if (clientError) return;
    setBusy(true);
    setError(null);
    setFields({});
    try {
      const app = await touristApi.submitApplication({
        areaId: Number(form.areaId),
        purpose: form.purpose,
        startDate: form.startDate,
        endDate: form.endDate,
      });
      navigate(`/tourist/applications/${app.id}`, { replace: true, state: { created: true } });
    } catch (err) {
      setError(errorMessage(err));
      setFields(fieldErrors(err));
    } finally {
      setBusy(false);
    }
  };

  if (!profile.complete) {
    return (
      <>
        <PageHeader title="Apply for a permit" />
        <Alert tone="info">
          Please <Link to="/tourist/profile">complete your profile</Link> before applying.
        </Alert>
      </>
    );
  }

  return (
    <>
      <PageHeader title="Apply for a permit" subtitle="Step 1 of 2 — trip details. You will upload documents next." />
      <Card>
        <form className="form" onSubmit={submit}>
          <Alert>{error ?? clientError}</Alert>
          <Field label="Permit-required area" error={fields.areaId}>
            <select
              value={form.areaId}
              onChange={(e) => setForm((f) => ({ ...f, areaId: e.target.value }))}
              required
            >
              <option value="">Select an area…</option>
              {areas.map((a) => (
                <option key={a.id} value={a.id}>
                  {a.name} ({a.code})
                </option>
              ))}
            </select>
          </Field>
          {selected && (
            <div className="info-box">
              <strong>{selected.name}</strong>
              {selected.description && <p>{selected.description}</p>}
              <p className="muted small">
                {[selected.district, selected.province].filter(Boolean).join(', ')} · Max {selected.maximumDays} days · Fee{' '}
                {Number(selected.fee).toFixed(2)}
              </p>
            </div>
          )}
          <div className="grid-2">
            <Field label="Start date" error={fields.startDate}>
              <input
                type="date"
                min={todayIso()}
                value={form.startDate}
                onChange={(e) => setForm((f) => ({ ...f, startDate: e.target.value }))}
                required
              />
            </Field>
            <Field label="End date" error={fields.endDate} hint={days > 0 ? `${days} day(s)` : undefined}>
              <input
                type="date"
                min={form.startDate || todayIso()}
                value={form.endDate}
                onChange={(e) => setForm((f) => ({ ...f, endDate: e.target.value }))}
                required
              />
            </Field>
          </div>
          <Field label="Purpose of visit" error={fields.purpose}>
            <textarea
              value={form.purpose}
              onChange={(e) => setForm((f) => ({ ...f, purpose: e.target.value }))}
              minLength={5}
              maxLength={1000}
              rows={3}
              required
            />
          </Field>
          <div className="form-actions">
            <Link className="btn btn-ghost" to="/tourist/applications">
              Cancel
            </Link>
            <button className="btn btn-primary" disabled={busy || !!clientError}>
              {busy ? 'Submitting…' : 'Submit & continue'}
            </button>
          </div>
        </form>
      </Card>

      {selected ? (
        <RouteMapCard key={selected.id} area={selected} />
      ) : (
        <RoadConditionsCard title="Road conditions" height={320} />
      )}
    </>
  );
}
