import { useState, type FormEvent } from 'react';
import { accountApi } from '../api/endpoints';
import { useAuth } from '../hooks/useAuth';
import type { UserInfo } from '../types';
import { errorMessage } from '../utils/errors';
import { Alert } from './ui';

/**
 * Change-password form. On success the server signs out every other session and
 * returns a new one, which replaces the current session.
 */
export function ChangePasswordForm({ onChanged, submitLabel = 'Change password' }: {
  onChanged?: (user: UserInfo) => void;
  submitLabel?: string;
}) {
  const { user, startSession } = useAuth();
  const minLength = user?.role === 'TOURIST' ? 8 : 12;
  const [current, setCurrent] = useState('');
  const [next, setNext] = useState('');
  const [confirm, setConfirm] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [done, setDone] = useState(false);
  const [busy, setBusy] = useState(false);

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    setError(null);
    setDone(false);
    if (next !== confirm) {
      setError('The new passwords do not match.');
      return;
    }
    if (next.length < minLength || !/[A-Za-z]/.test(next) || !/\d/.test(next)) {
      setError(`Use at least ${minLength} characters, including a letter and a digit.`);
      return;
    }
    setBusy(true);
    try {
      const u = startSession(await accountApi.changePassword({ currentPassword: current, newPassword: next }));
      setCurrent('');
      setNext('');
      setConfirm('');
      setDone(true);
      onChanged?.(u);
    } catch (err) {
      setError(errorMessage(err));
    } finally {
      setBusy(false);
    }
  };

  return (
    <form onSubmit={submit} className="stack">
      <Alert>{error}</Alert>
      {done && <Alert tone="success">Password changed. Other devices have been signed out.</Alert>}
      <label className="field">
        <span className="field-label">Current password</span>
        <input type="password" autoComplete="current-password" value={current}
          onChange={(e) => setCurrent(e.target.value)} required maxLength={128} />
      </label>
      <label className="field">
        <span className="field-label">New password</span>
        <input type="password" autoComplete="new-password" value={next} minLength={minLength}
          onChange={(e) => setNext(e.target.value)} required maxLength={128} />
        <span className="field-hint">At least {minLength} characters with a letter and a digit.</span>
      </label>
      <label className="field">
        <span className="field-label">Confirm new password</span>
        <input type="password" autoComplete="new-password" value={confirm}
          onChange={(e) => setConfirm(e.target.value)} required maxLength={128} />
      </label>
      <div>
        <button className="btn btn-primary" disabled={busy}>{busy ? 'Saving…' : submitLabel}</button>
      </div>
    </form>
  );
}
