import { useState, type ChangeEvent, type FormEvent } from 'react';
import { Link, Navigate, useNavigate } from 'react-router-dom';
import { Alert, Field } from '../../components/ui';
import { homePathFor, useAuth } from '../../hooks/useAuth';
import { errorMessage, fieldErrors } from '../../utils/errors';

export function RegisterPage() {
  const { user, register } = useAuth();
  const navigate = useNavigate();
  const [form, setForm] = useState({ firstName: '', lastName: '', email: '', password: '', confirm: '' });
  const [error, setError] = useState<string | null>(null);
  const [fields, setFields] = useState<Record<string, string>>({});
  const [busy, setBusy] = useState(false);

  if (user) return <Navigate to={homePathFor(user)} replace />;

  const set = (k: keyof typeof form) => (e: ChangeEvent<HTMLInputElement>) =>
    setForm((f) => ({ ...f, [k]: e.target.value }));

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    setError(null);
    setFields({});
    if (form.password !== form.confirm) {
      setFields({ confirm: 'Passwords do not match' });
      return;
    }
    setBusy(true);
    try {
      await register({ email: form.email, password: form.password, firstName: form.firstName, lastName: form.lastName });
      navigate('/tourist/profile', { replace: true, state: { welcome: true } });
    } catch (err) {
      setError(errorMessage(err));
      setFields(fieldErrors(err));
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="auth-screen">
      <form className="auth-card" onSubmit={submit}>
        <div className="auth-brand">
          <img src="/favicon.svg" alt="" width={44} height={44} />
          <h1>Create tourist account</h1>
          <p className="muted">Apply for permits to restricted areas</p>
        </div>
        <Alert>{error}</Alert>
        <div className="grid-2">
          <Field label="First name" error={fields.firstName}>
            <input value={form.firstName} onChange={set('firstName')} maxLength={100} required autoComplete="given-name" />
          </Field>
          <Field label="Last name" error={fields.lastName}>
            <input value={form.lastName} onChange={set('lastName')} maxLength={100} required autoComplete="family-name" />
          </Field>
        </div>
        <Field label="Email" error={fields.email}>
          <input type="email" value={form.email} onChange={set('email')} required autoComplete="email" />
        </Field>
        <Field label="Password" error={fields.password} hint="At least 8 characters, including a letter and a digit">
          <input
            type="password"
            value={form.password}
            onChange={set('password')}
            minLength={8}
            maxLength={128}
            required
            autoComplete="new-password"
          />
        </Field>
        <Field label="Confirm password" error={fields.confirm}>
          <input type="password" value={form.confirm} onChange={set('confirm')} required autoComplete="new-password" />
        </Field>
        <button className="btn btn-primary btn-block" disabled={busy}>
          {busy ? 'Creating account…' : 'Create account'}
        </button>
        <p className="muted center">
          Already registered? <Link to="/login">Sign in</Link>
        </p>
      </form>
    </div>
  );
}
