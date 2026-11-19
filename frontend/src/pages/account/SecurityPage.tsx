import { useState, type FormEvent } from 'react';
import { accountApi } from '../../api/endpoints';
import { ChangePasswordForm } from '../../components/ChangePasswordForm';
import { Alert, Card, DetailList, PageHeader, Spinner } from '../../components/ui';
import { useAsync } from '../../hooks/useAsync';
import type { MfaSetup } from '../../types';
import { errorMessage } from '../../utils/errors';
import { formatDateTime, humanize } from '../../utils/format';
import { saveBlob } from '../../utils/download';

/** Password and two-step verification (TOTP) settings for the signed-in user. */
export function SecurityPage() {
  const account = useAsync(() => accountApi.me(), []);
  const [setup, setSetup] = useState<MfaSetup | null>(null);
  const [codes, setCodes] = useState<string[] | null>(null);
  const [code, setCode] = useState('');
  const [mode, setMode] = useState<'disable' | 'regenerate' | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const a = account.data;

  const run = async (action: () => Promise<void>) => {
    setBusy(true);
    setError(null);
    try {
      await action();
    } catch (err) {
      setError(errorMessage(err));
    } finally {
      setBusy(false);
    }
  };

  const beginSetup = () => run(async () => {
    setCodes(null);
    setSetup(await accountApi.startMfaSetup());
    setCode('');
  });

  const confirmSetup = (e: FormEvent) => {
    e.preventDefault();
    return run(async () => {
      setCodes(await accountApi.enableMfa(code.trim()));
      setSetup(null);
      setCode('');
      account.reload();
    });
  };

  const confirmAction = (e: FormEvent) => {
    e.preventDefault();
    return run(async () => {
      if (mode === 'disable') {
        await accountApi.disableMfa(code.trim());
      } else {
        setCodes(await accountApi.regenerateRecoveryCodes(code.trim()));
      }
      setMode(null);
      setCode('');
      account.reload();
    });
  };

  const downloadCodes = () => {
    if (!codes) return;
    const text = `YatraPass recovery codes for ${a?.email}\nEach code can be used once.\n\n${codes.join('\n')}\n`;
    saveBlob(new Blob([text], { type: 'text/plain' }), 'yatrapass-recovery-codes.txt');
  };

  return (
    <div className="page">
      <PageHeader title="Account security" subtitle="Password and two-step verification" />
      {account.loading && !a && <Spinner />}
      <Alert>{account.error}</Alert>

      {a && (
        <div className="grid-2">
          <Card title="Account">
            <DetailList items={[
              ['Email', a.email],
              ['Role', humanize(a.role)],
              ['Name', a.displayName ?? '—'],
              ['Last sign-in', formatDateTime(a.lastLoginAt)],
              ['Two-step verification', a.mfaEnabled ? 'On' : 'Off'],
            ]} />
          </Card>

          <Card title="Change password">
            <ChangePasswordForm />
          </Card>

          <Card title="Two-step verification" className="span-2">
            <Alert>{error}</Alert>
            {codes && (
              <div className="recovery-box">
                <Alert tone="success">
                  Save these recovery codes somewhere safe. Each works once if you lose your phone.
                  They will not be shown again.
                </Alert>
                <ol className="recovery-codes">
                  {codes.map((c) => <li key={c}><code>{c}</code></li>)}
                </ol>
                <div className="row-actions">
                  <button className="btn btn-ghost" onClick={downloadCodes}>Download codes</button>
                  <button className="btn btn-ghost" onClick={() => setCodes(null)}>I have saved them</button>
                </div>
              </div>
            )}

            {!a.mfaEnabled && !setup && (
              <div className="stack">
                <p className="muted">
                  Protect your account with a 6-digit code from an authenticator app (Google Authenticator,
                  Microsoft Authenticator, Authy…) in addition to your password.
                  {a.role === 'ADMIN' && ' Strongly recommended for administrators.'}
                </p>
                <div><button className="btn btn-primary" onClick={beginSetup} disabled={busy}>Set up two-step verification</button></div>
              </div>
            )}

            {setup && (
              <form className="mfa-setup" onSubmit={confirmSetup}>
                <div className="mfa-qr">
                  <img src={setup.qrPngDataUrl} alt="QR code for your authenticator app" width={200} height={200} />
                </div>
                <div className="stack">
                  <ol className="steps">
                    <li>Scan this QR code with your authenticator app.</li>
                    <li>
                      Can't scan? Enter this key manually:
                      <code className="secret-key">{setup.secret.replace(/(.{4})/g, '$1 ').trim()}</code>
                    </li>
                    <li>Enter the 6-digit code the app shows.</li>
                  </ol>
                  <label className="field">
                    <span className="field-label">Code</span>
                    <input className="code-input" inputMode="numeric" pattern="[0-9]{6}" maxLength={6} autoComplete="one-time-code"
                      value={code} onChange={(e) => setCode(e.target.value.replace(/\D/g, ''))} required placeholder="123456" />
                  </label>
                  <div className="row-actions">
                    <button className="btn btn-primary" disabled={busy || code.length !== 6}>Turn on</button>
                    <button type="button" className="btn btn-ghost" onClick={() => setSetup(null)}>Cancel</button>
                  </div>
                </div>
              </form>
            )}

            {a.mfaEnabled && !mode && (
              <div className="stack">
                <p>
                  <span className="badge badge-ok">On</span>{' '}
                  {a.recoveryCodesRemaining} unused recovery code{a.recoveryCodesRemaining === 1 ? '' : 's'} left.
                </p>
                {a.recoveryCodesRemaining <= 3 && (
                  <Alert tone="info">You are running low on recovery codes. Generate a new set.</Alert>
                )}
                <div className="row-actions">
                  <button className="btn btn-ghost" onClick={() => { setMode('regenerate'); setCode(''); }}>
                    New recovery codes
                  </button>
                  <button className="btn btn-danger" onClick={() => { setMode('disable'); setCode(''); }}>
                    Turn off
                  </button>
                </div>
              </div>
            )}

            {mode && (
              <form className="stack" onSubmit={confirmAction}>
                <p className="muted">
                  {mode === 'disable'
                    ? 'Enter a current authenticator code to turn off two-step verification.'
                    : 'Enter a current authenticator code. Your old recovery codes will stop working.'}
                </p>
                <label className="field">
                  <span className="field-label">Authenticator code</span>
                  <input className="code-input" inputMode="numeric" pattern="[0-9]{6}" maxLength={6} autoComplete="one-time-code"
                    value={code} onChange={(e) => setCode(e.target.value.replace(/\D/g, ''))} required />
                </label>
                <div className="row-actions">
                  <button className={`btn ${mode === 'disable' ? 'btn-danger' : 'btn-primary'}`} disabled={busy || code.length !== 6}>
                    {mode === 'disable' ? 'Turn off' : 'Generate codes'}
                  </button>
                  <button type="button" className="btn btn-ghost" onClick={() => setMode(null)}>Cancel</button>
                </div>
              </form>
            )}
          </Card>
        </div>
      )}
    </div>
  );
}
