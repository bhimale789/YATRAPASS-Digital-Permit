import { Navigate, useNavigate } from 'react-router-dom';
import { ChangePasswordForm } from '../../components/ChangePasswordForm';
import { Spinner } from '../../components/ui';
import { homePathFor, useAuth } from '../../hooks/useAuth';

/** Shown after signing in with a temporary password issued by an administrator. */
export function ForcedPasswordChangePage() {
  const { user, initializing, logout } = useAuth();
  const navigate = useNavigate();

  if (initializing) return <div className="center-screen"><Spinner /></div>;
  if (!user) return <Navigate to="/login" replace />;

  return (
    <div className="auth-screen">
      <div className="auth-card">
        <div className="auth-brand">
          <img src="/favicon.svg" alt="" width={44} height={44} />
          <h1>Choose a new password</h1>
          <p className="muted">
            {user.passwordChangeRequired
              ? 'Your account uses a temporary password. Set your own password to continue.'
              : 'Update the password for ' + user.email}
          </p>
        </div>
        <ChangePasswordForm submitLabel="Save and continue"
          onChanged={(u) => navigate(homePathFor(u), { replace: true })} />
        <div className="auth-links">
          <button type="button" className="link-btn" onClick={async () => { await logout(); navigate('/login'); }}>
            Sign out
          </button>
        </div>
      </div>
    </div>
  );
}
