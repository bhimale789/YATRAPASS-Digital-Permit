import { Link, useParams } from 'react-router-dom';
import { verificationApi } from '../api/endpoints';
import { VerificationResultPanel } from '../components/VerificationResultPanel';
import { Alert, Spinner } from '../components/ui';
import { homePathFor, useAuth } from '../hooks/useAuth';
import { useAsync } from '../hooks/useAsync';

/**
 * Target of the URL inside every permit QR code. Verification is only performed for
 * signed-in ADMIN/OFFICER users; anyone else sees a neutral notice and no permit data.
 */
export function VerifyLandingPage() {
  const { token = '' } = useParams();
  const { user, initializing } = useAuth();
  const canVerify = user?.role === 'ADMIN' || user?.role === 'OFFICER';

  if (initializing) return <div className="center-screen"><Spinner /></div>;

  return (
    <div className="auth-screen">
      <div className="verify-landing">
        <div className="auth-brand">
          <img src="/favicon.svg" alt="" width={44} height={44} />
          <h1>Permit Verification</h1>
        </div>
        {canVerify ? (
          <AutoVerify token={token} />
        ) : (
          <>
            <Alert tone="info">
              This is an official tourist permit QR code. It can only be verified by authorized permit officers.
            </Alert>
            {!user ? (
              <Link className="btn btn-primary btn-block" to="/login" state={{ from: `/verify/${token}` }}>
                Officer sign in
              </Link>
            ) : (
              <Link className="btn btn-ghost btn-block" to={homePathFor(user)}>Back to my dashboard</Link>
            )}
          </>
        )}
      </div>
    </div>
  );
}

function AutoVerify({ token }: { token: string }) {
  const { data, error, loading } = useAsync(() => verificationApi.verify(token), [token]);
  if (loading) return <Spinner label="Verifying…" />;
  if (error || !data) return <Alert>{error}</Alert>;
  return (
    <>
      <VerificationResultPanel result={data} />
      <Link className="btn btn-primary btn-block" to="/admin/scanner">Open scanner</Link>
    </>
  );
}
