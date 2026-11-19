import { useEffect, useRef, useState, type FormEvent, type ReactNode } from 'react';
import { adminApi } from '../../api/endpoints';
import { ResponsiveTable } from '../../components/ResponsiveTable';
import { Alert, Card, EmptyState, PageHeader, Pagination, Spinner } from '../../components/ui';
import { useAsync } from '../../hooks/useAsync';
import { useAuth } from '../../hooks/useAuth';
import { useDebounce } from '../../hooks/useDebounce';
import type { Role, StaffCredentials, StaffMember } from '../../types';
import { errorMessage } from '../../utils/errors';
import { formatDateTime } from '../../utils/format';

type Editing = { mode: 'create' } | { mode: 'edit'; staff: StaffMember };

/** Administrator and checkpoint-officer accounts. */
export function StaffPage() {
  const { user } = useAuth();
  const [q, setQ] = useState('');
  const [role, setRole] = useState<Role | ''>('');
  const [page, setPage] = useState(0);
  const debouncedQ = useDebounce(q);
  const list = useAsync(() => adminApi.staff({ q: debouncedQ, role: role || undefined, page, size: 20 }),
    [debouncedQ, role, page]);
  const [editing, setEditing] = useState<Editing | null>(null);
  const [credentials, setCredentials] = useState<StaffCredentials | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);

  const act = async (action: () => Promise<unknown>, success?: string) => {
    setError(null);
    setNotice(null);
    try {
      await action();
      if (success) setNotice(success);
      list.reload();
    } catch (err) {
      setError(errorMessage(err));
    }
  };

  const resetPassword = (s: StaffMember) => {
    if (!window.confirm(`Issue a new temporary password for ${s.email}? Their current password and sessions stop working.`)) return;
    void act(async () => setCredentials(await adminApi.resetStaffPassword(s.id)));
  };

  const resetMfa = (s: StaffMember) => {
    if (!window.confirm(`Turn off two-step verification for ${s.email}? Use this only if they lost their phone.`)) return;
    void act(() => adminApi.resetStaffMfa(s.id), `Two-step verification was reset for ${s.email}.`);
  };

  const toggle = (s: StaffMember) => {
    if (s.enabled && !window.confirm(`Disable ${s.email}? They will be signed out and cannot sign in.`)) return;
    void act(() => adminApi.setStaffEnabled(s.id, !s.enabled),
      `${s.email} was ${s.enabled ? 'disabled' : 'enabled'}.`);
  };

  return (
    <>
      <PageHeader
        title="Staff accounts"
        subtitle="Administrators and checkpoint officers"
        actions={<button className="btn btn-primary" onClick={() => setEditing({ mode: 'create' })}>Add staff</button>}
      />
      <Card>
        <div className="toolbar">
          <input type="search" placeholder="Name or email…" value={q} maxLength={100}
            onChange={(e) => { setQ(e.target.value); setPage(0); }} />
          <select value={role} onChange={(e) => { setRole(e.target.value as Role | ''); setPage(0); }} aria-label="Role">
            <option value="">All roles</option>
            <option value="ADMIN">Administrators</option>
            <option value="OFFICER">Officers</option>
          </select>
        </div>
        <Alert>{error ?? list.error}</Alert>
        <Alert tone="success">{notice}</Alert>
        {list.loading && !list.data ? (
          <Spinner />
        ) : !list.data?.content.length ? (
          <EmptyState>No staff accounts match.</EmptyState>
        ) : (
          <ResponsiveTable>
            <table>
              <thead>
                <tr>
                  <th>Name</th>
                  <th>Email</th>
                  <th>Role</th>
                  <th>Status</th>
                  <th>2-step</th>
                  <th>Last sign-in</th>
                  <th />
                </tr>
              </thead>
              <tbody>
                {list.data.content.map((s) => {
                  const self = s.id === user?.id;
                  return (
                    <tr key={s.id}>
                      <td>{s.displayName ?? '—'}{self && <span className="muted"> (you)</span>}</td>
                      <td>{s.email}</td>
                      <td>{s.role === 'ADMIN' ? 'Administrator' : 'Officer'}</td>
                      <td>
                        {!s.enabled ? <span className="badge badge-bad">Disabled</span>
                          : s.passwordChangeRequired ? <span className="badge badge-warn">Awaiting first sign-in</span>
                            : <span className="badge badge-ok">Active</span>}
                      </td>
                      <td>{s.mfaEnabled ? <span className="badge badge-ok">On</span> : <span className="badge badge-muted">Off</span>}</td>
                      <td>{s.lastLoginAt ? formatDateTime(s.lastLoginAt) : 'Never'}</td>
                      <td className="row-actions">
                        {!self && (
                          <>
                            <button className="btn btn-ghost btn-sm" onClick={() => setEditing({ mode: 'edit', staff: s })}>Edit</button>
                            <button className="btn btn-ghost btn-sm" onClick={() => resetPassword(s)}>Reset password</button>
                            {s.mfaEnabled && <button className="btn btn-ghost btn-sm" onClick={() => resetMfa(s)}>Reset 2-step</button>}
                            <button className={`btn btn-sm ${s.enabled ? 'btn-danger-ghost' : 'btn-ghost'}`} onClick={() => toggle(s)}>
                              {s.enabled ? 'Disable' : 'Enable'}
                            </button>
                          </>
                        )}
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </ResponsiveTable>
        )}
        <Pagination page={list.data} onChange={setPage} />
      </Card>

      {editing && (
        <StaffDialog
          editing={editing}
          onClose={() => setEditing(null)}
          onSaved={(result) => {
            setEditing(null);
            list.reload();
            if ('temporaryPassword' in result) setCredentials(result);
            else setNotice(`${result.email} was updated.`);
          }}
        />
      )}

      {credentials && (
        <Modal onClose={() => setCredentials(null)}>
          <h2>Temporary password</h2>
          <p className="muted">
            Give this password to <strong>{credentials.staff.email}</strong> in person or over a secure channel.
            It is shown only once; they must choose their own password when they first sign in.
          </p>
          <code className="temp-password">{credentials.temporaryPassword}</code>
          <div className="dialog-actions">
            <button className="btn btn-ghost" onClick={() => navigator.clipboard?.writeText(credentials.temporaryPassword)}>Copy</button>
            <button className="btn btn-primary" onClick={() => setCredentials(null)}>Done</button>
          </div>
        </Modal>
      )}
    </>
  );
}

function StaffDialog({ editing, onClose, onSaved }: {
  editing: Editing;
  onClose: () => void;
  onSaved: (result: StaffCredentials | StaffMember) => void;
}) {
  const existing = editing.mode === 'edit' ? editing.staff : null;
  const [email, setEmail] = useState(existing?.email ?? '');
  const [displayName, setDisplayName] = useState(existing?.displayName ?? '');
  const [role, setRole] = useState<Role>(existing?.role ?? 'OFFICER');
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      onSaved(existing
        ? await adminApi.updateStaff(existing.id, { displayName: displayName.trim(), role })
        : await adminApi.createStaff({ email: email.trim(), displayName: displayName.trim(), role }));
    } catch (err) {
      setError(errorMessage(err));
    } finally {
      setBusy(false);
    }
  };

  return (
    <Modal onClose={onClose}>
      <form onSubmit={submit}>
        <h2>{existing ? 'Edit staff account' : 'Add staff account'}</h2>
        <Alert>{error}</Alert>
        <label className="field">
          <span className="field-label">Email</span>
          <input type="email" value={email} onChange={(e) => setEmail(e.target.value)} required maxLength={255}
            disabled={!!existing} autoFocus={!existing} />
        </label>
        <label className="field">
          <span className="field-label">Name / checkpoint</span>
          <input value={displayName} onChange={(e) => setDisplayName(e.target.value)} required maxLength={100}
            placeholder="e.g. Jomsom Checkpoint" />
        </label>
        <label className="field">
          <span className="field-label">Role</span>
          <select value={role} onChange={(e) => setRole(e.target.value as Role)}>
            <option value="OFFICER">Officer: scans and verifies permits</option>
            <option value="ADMIN">Administrator: full access</option>
          </select>
        </label>
        {!existing && (
          <p className="field-hint">A temporary password is generated. The new user must change it at first sign-in.</p>
        )}
        <div className="dialog-actions">
          <button type="button" className="btn btn-ghost" onClick={onClose} disabled={busy}>Cancel</button>
          <button className="btn btn-primary" disabled={busy}>{busy ? 'Saving…' : existing ? 'Save' : 'Create account'}</button>
        </div>
      </form>
    </Modal>
  );
}

/** Native <dialog> shown modally while mounted. */
function Modal({ children, onClose }: { children: ReactNode; onClose: () => void }) {
  const ref = useRef<HTMLDialogElement>(null);
  useEffect(() => {
    const d = ref.current;
    if (d && !d.open) d.showModal();
    return () => d?.close();
  }, []);
  return <dialog ref={ref} className="dialog" onClose={onClose}>{children}</dialog>;
}
