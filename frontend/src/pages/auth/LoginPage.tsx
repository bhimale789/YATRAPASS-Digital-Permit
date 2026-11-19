import { useState, type FormEvent } from 'react';
import { Link, Navigate, useLocation, useNavigate } from 'react-router-dom';
import { Alert } from '../../components/ui';
import { homePathFor, useAuth } from '../../hooks/useAuth';
import type { UserInfo } from '../../types';
import { errorMessage } from '../../utils/errors';

export function LoginPage() {
  const { user, login, loginMfa } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [mfaToken, setMfaToken] = useState<string | null>(null);
  const [code, setCode] = useState('');
  const [useRecovery, setUseRecovery] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  if (user) return <Navigate to={homePathFor(user)} replace />;

  const proceed = (u: UserInfo) => {
    const from = (location.state as { from?: string } | null)?.from;
    const target = u.passwordChangeRequired ? '/account/password'
      : from && from !== '/login' ? from : homePathFor(u);
    navigate(target, { replace: true });
  };

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      if (mfaToken) {
        proceed(await loginMfa(mfaToken, code.trim()));
      } else {
        const outcome = await login(email, password);
        if (outcome.mfaToken) {
          setMfaToken(outcome.mfaToken);
          setPassword('');
        } else if (outcome.user) {
          proceed(outcome.user);
        }
      }
    } catch (err) {
      setError(errorMessage(err));
      // The challenge token is short-lived; start again if it has expired.
      if (mfaToken && /expired|sign in again/i.test(errorMessage(err))) setMfaToken(null);
    } finally {
      setBusy(false);
    }
  };

  const restart = () => {
    setMfaToken(null);
    setCode('');
    setError(null);
  };

  return (
    <div className="auth-screen">
      <form className="auth-card" onSubmit={submit}>
        <div className="auth-brand">
          <img src="/favicon.svg" alt="" width={44} height={44} />
          <h1>YatraPass</h1>
          <p className="muted">{mfaToken ? 'Two-step verification' : 'Sign in to manage permits'}</p>
        </div>
        <Alert>{error}</Alert>

        {!mfaToken ? (
          <>
            <label className="field">
              <span className="field-label">Email</span>
              <input type="email" autoComplete="username" value={email} onChange={(e) => setEmail(e.target.value)} required />
            </label>
            <label className="field">
              <span className="field-label">Password</span>
              <input
                type="password"
                autoComplete="current-password"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                required
              />
            </label>
            <button className="btn btn-primary btn-block" disabled={busy}>
              {busy ? 'Signing in…' : 'Sign in'}
            </button>
            <p className="muted center">
              New tourist? <Link to="/register">Create an account</Link>
            </p>
          </>
        ) : (
          <>
            <p className="muted small">
              {useRecovery
                ? 'Enter one of the recovery codes you saved when you turned on two-step verification.'
                : 'Open your authenticator app and enter the 6-digit code for YatraPass.'}
            </p>
            <label className="field">
              <span className="field-label">{useRecovery ? 'Recovery code' : 'Authentication code'}</span>
              <input
                className="code-input"
                autoFocus
                autoComplete="one-time-code"
                inputMode={useRecovery ? 'text' : 'numeric'}
                pattern={useRecovery ? undefined : '[0-9]{6}'}
                maxLength={useRecovery ? 11 : 6}
                placeholder={useRecovery ? 'XXXXX-XXXXX' : '123456'}
                value={code}
                onChange={(e) => setCode(useRecovery ? e.target.value.toUpperCase() : e.target.value.replace(/\D/g, ''))}
                required
              />
            </label>
            <button className="btn btn-primary btn-block" disabled={busy}>
              {busy ? 'Verifying…' : 'Verify'}
            </button>
            <div className="auth-links">
              <button type="button" className="link-btn" onClick={() => { setUseRecovery((r) => !r); setCode(''); }}>
                {useRecovery ? 'Use authenticator code' : 'Use a recovery code'}
              </button>
              <button type="button" className="link-btn" onClick={restart}>Back</button>
            </div>
          </>
        )}
      </form>
    </div>
  );
}
