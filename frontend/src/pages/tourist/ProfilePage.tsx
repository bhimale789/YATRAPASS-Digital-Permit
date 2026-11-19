import { useEffect, useState, type FormEvent } from 'react';
import { useLocation } from 'react-router-dom';
import { touristApi, type ProfileUpdate } from '../../api/endpoints';
import { Alert, Card, Field, PageHeader, Spinner } from '../../components/ui';
import { useAsync } from '../../hooks/useAsync';
import type { Gender } from '../../types';
import { errorMessage, fieldErrors } from '../../utils/errors';

const EMPTY: ProfileUpdate = {
  firstName: '',
  lastName: '',
  passportNumber: '',
  nationality: '',
  dateOfBirth: '',
  gender: null,
  phone: '',
  address: '',
  emergencyContact: '',
};

export function ProfilePage() {
  const location = useLocation();
  const welcome = (location.state as { welcome?: boolean } | null)?.welcome;
  const { data: profile, error: loadError, loading, setData } = useAsync(() => touristApi.profile(), []);
  const [form, setForm] = useState<ProfileUpdate>(EMPTY);
  const [error, setError] = useState<string | null>(null);
  const [fields, setFields] = useState<Record<string, string>>({});
  const [saved, setSaved] = useState(false);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    if (profile) {
      setForm({
        firstName: profile.firstName,
        lastName: profile.lastName,
        passportNumber: profile.passportNumber ?? '',
        nationality: profile.nationality ?? '',
        dateOfBirth: profile.dateOfBirth ?? '',
        gender: profile.gender,
        phone: profile.phone ?? '',
        address: profile.address ?? '',
        emergencyContact: profile.emergencyContact ?? '',
      });
    }
  }, [profile]);

  if (loading) return <Spinner />;
  if (loadError) return <Alert>{loadError}</Alert>;

  const set = (k: keyof ProfileUpdate) => (e: { target: { value: string } }) =>
    setForm((f) => ({ ...f, [k]: e.target.value }));

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    setBusy(true);
    setError(null);
    setFields({});
    setSaved(false);
    try {
      const updated = await touristApi.updateProfile({
        ...form,
        gender: form.gender || null,
        address: form.address || null,
        emergencyContact: form.emergencyContact || null,
      });
      setData(updated);
      setSaved(true);
    } catch (err) {
      setError(errorMessage(err));
      setFields(fieldErrors(err));
    } finally {
      setBusy(false);
    }
  };

  return (
    <>
      <PageHeader title="My Profile" subtitle={profile?.email} />
      {welcome && !profile?.complete && (
        <Alert tone="info">Welcome! Complete your profile to start applying for permits.</Alert>
      )}
      <Card>
        <form className="form" onSubmit={submit}>
          <Alert>{error}</Alert>
          {saved && <Alert tone="success">Profile saved.</Alert>}
          <div className="grid-2">
            <Field label="First name" error={fields.firstName}>
              <input value={form.firstName} onChange={set('firstName')} maxLength={100} required />
            </Field>
            <Field label="Last name" error={fields.lastName}>
              <input value={form.lastName} onChange={set('lastName')} maxLength={100} required />
            </Field>
            <Field label="Passport number" error={fields.passportNumber} hint="Letters and digits only">
              <input
                value={form.passportNumber ?? ''}
                onChange={set('passportNumber')}
                pattern="[A-Za-z0-9]{5,20}"
                maxLength={20}
                required
                autoComplete="off"
              />
            </Field>
            <Field label="Nationality" error={fields.nationality}>
              <input value={form.nationality ?? ''} onChange={set('nationality')} maxLength={100} required />
            </Field>
            <Field label="Date of birth" error={fields.dateOfBirth}>
              <input type="date" value={form.dateOfBirth ?? ''} onChange={set('dateOfBirth')} required />
            </Field>
            <Field label="Gender" error={fields.gender}>
              <select
                value={form.gender ?? ''}
                onChange={(e) => setForm((f) => ({ ...f, gender: (e.target.value || null) as Gender | null }))}
              >
                <option value="">Prefer not to say</option>
                <option value="FEMALE">Female</option>
                <option value="MALE">Male</option>
                <option value="OTHER">Other</option>
                <option value="UNDISCLOSED">Undisclosed</option>
              </select>
            </Field>
            <Field label="Phone" error={fields.phone}>
              <input type="tel" value={form.phone ?? ''} onChange={set('phone')} maxLength={30} required />
            </Field>
            <Field label="Emergency contact" error={fields.emergencyContact}>
              <input value={form.emergencyContact ?? ''} onChange={set('emergencyContact')} maxLength={255} />
            </Field>
          </div>
          <Field label="Address" error={fields.address}>
            <textarea value={form.address ?? ''} onChange={set('address')} maxLength={500} rows={2} />
          </Field>
          <div className="form-actions">
            <button className="btn btn-primary" disabled={busy}>
              {busy ? 'Saving…' : 'Save profile'}
            </button>
          </div>
        </form>
      </Card>
    </>
  );
}
