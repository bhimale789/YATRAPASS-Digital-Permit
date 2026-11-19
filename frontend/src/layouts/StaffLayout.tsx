import { useAuth } from '../hooks/useAuth';
import { AdminLayout } from './AdminLayout';
import { AppLayout } from './AppLayout';

/** Pages shared by admins and officers (the scanner) use each role's own shell. */
export function StaffLayout() {
  const { user } = useAuth();
  return user?.role === 'ADMIN' ? <AdminLayout /> : <AppLayout />;
}
