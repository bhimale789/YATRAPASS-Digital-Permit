import { Navigate, Outlet, useLocation } from 'react-router-dom';
import { Spinner } from '../components/ui';
import { homePathFor, useAuth } from '../hooks/useAuth';
import type { Role } from '../types';

/**
 * Client-side route guard for UX only. Every API call is independently authorized
 * by the backend, which never trusts roles supplied by the client.
 */
export function ProtectedRoute({ roles }: { roles: Role[] }) {
  const { user, initializing } = useAuth();
  const location = useLocation();

  if (initializing) return <div className="center-screen"><Spinner /></div>;
  if (!user) return <Navigate to="/login" replace state={{ from: location.pathname + location.search }} />;
  if (user.passwordChangeRequired) return <Navigate to="/account/password" replace />;
  if (!roles.includes(user.role)) return <Navigate to={homePathFor(user)} replace />;
  return <Outlet />;
}
