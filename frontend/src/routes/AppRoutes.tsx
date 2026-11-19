import { lazy, Suspense } from 'react';
import { Navigate, Route, Routes } from 'react-router-dom';
import { homePathFor, useAuth } from '../hooks/useAuth';
import { AppLayout } from '../layouts/AppLayout';
import { TouristLayout } from '../layouts/TouristLayout';
import { AdminLayout } from '../layouts/AdminLayout';
import { StaffLayout } from '../layouts/StaffLayout';
import { LoginPage } from '../pages/auth/LoginPage';
import { OfficerDashboard } from '../pages/officer/OfficerDashboard';
import { RegisterPage } from '../pages/auth/RegisterPage';
import { ApplicationDetailPage } from '../pages/tourist/ApplicationDetailPage';
import { ApplicationsPage } from '../pages/tourist/ApplicationsPage';
import { ExplorePage } from '../pages/tourist/ExplorePage';
import { NewApplicationPage } from '../pages/tourist/NewApplicationPage';
import { PermitsPage } from '../pages/tourist/PermitsPage';
import { ProfilePage } from '../pages/tourist/ProfilePage';
import { TouristDashboard } from '../pages/tourist/TouristDashboard';
import { VerifyLandingPage } from '../pages/VerifyLandingPage';
import { ForcedPasswordChangePage } from '../pages/auth/ForcedPasswordChangePage';
import { SecurityPage } from '../pages/account/SecurityPage';
import { EsewaReturnPage } from '../pages/tourist/EsewaReturnPage';
import { NotificationsPage } from '../pages/tourist/NotificationsPage';
import { ProtectedRoute } from './ProtectedRoute';
import { Spinner } from '../components/ui';

// Administrator pages are loaded on demand so tourists download less.
const AdminApplicationReviewPage = lazy(() => import('../pages/admin/AdminApplicationReviewPage').then((m) => ({ default: m.AdminApplicationReviewPage })));
const AdminApplicationsPage = lazy(() => import('../pages/admin/AdminApplicationsPage').then((m) => ({ default: m.AdminApplicationsPage })));
const AdminAreasPage = lazy(() => import('../pages/admin/AdminAreasPage').then((m) => ({ default: m.AdminAreasPage })));
const AdminDashboard = lazy(() => import('../pages/admin/AdminDashboard').then((m) => ({ default: m.AdminDashboard })));
const AdminPermitDetailPage = lazy(() => import('../pages/admin/AdminPermitDetailPage').then((m) => ({ default: m.AdminPermitDetailPage })));
const AdminPermitsPage = lazy(() => import('../pages/admin/AdminPermitsPage').then((m) => ({ default: m.AdminPermitsPage })));
const AdminTouristDetailPage = lazy(() => import('../pages/admin/AdminTouristDetailPage').then((m) => ({ default: m.AdminTouristDetailPage })));
const AdminTouristsPage = lazy(() => import('../pages/admin/AdminTouristsPage').then((m) => ({ default: m.AdminTouristsPage })));
const AuditLogPage = lazy(() => import('../pages/admin/AuditLogPage').then((m) => ({ default: m.AuditLogPage })));
const VerificationsPage = lazy(() => import('../pages/admin/VerificationsPage').then((m) => ({ default: m.VerificationsPage })));
const RoadConditionsPage = lazy(() => import('../pages/admin/RoadConditionsPage').then((m) => ({ default: m.RoadConditionsPage })));
const StaffPage = lazy(() => import('../pages/admin/StaffPage').then((m) => ({ default: m.StaffPage })));
const PaymentsPage = lazy(() => import('../pages/admin/PaymentsPage').then((m) => ({ default: m.PaymentsPage })));

// The scanner pulls in the QR decoding library; load it only for staff who open it.
const ScannerPage = lazy(() => import('../pages/admin/ScannerPage').then((m) => ({ default: m.ScannerPage })));

function Home() {
  const { user, initializing } = useAuth();
  if (initializing) return <div className="center-screen"><Spinner /></div>;
  return <Navigate to={homePathFor(user)} replace />;
}

export function AppRoutes() {
  return (
    <Suspense fallback={<div className="center-screen"><Spinner /></div>}>
    <Routes>
      <Route path="/" element={<Home />} />
      <Route path="/login" element={<LoginPage />} />
      <Route path="/register" element={<RegisterPage />} />
      <Route path="/verify/:token" element={<VerifyLandingPage />} />
      <Route path="/account/password" element={<ForcedPasswordChangePage />} />

      <Route element={<ProtectedRoute roles={['TOURIST']} />}>
        <Route element={<TouristLayout />}>
          <Route path="/tourist/dashboard" element={<TouristDashboard />} />
          <Route path="/tourist/explore" element={<ExplorePage />} />
          <Route path="/tourist/profile" element={<ProfilePage />} />
          <Route path="/tourist/applications" element={<ApplicationsPage />} />
          <Route path="/tourist/applications/new" element={<NewApplicationPage />} />
          <Route path="/tourist/applications/:id" element={<ApplicationDetailPage />} />
          <Route path="/tourist/permits" element={<PermitsPage />} />
          <Route path="/tourist/notifications" element={<NotificationsPage />} />
          <Route path="/tourist/security" element={<SecurityPage />} />
          <Route path="/tourist/payments/esewa/success" element={<EsewaReturnPage outcome="success" />} />
          <Route path="/tourist/payments/esewa/failure" element={<EsewaReturnPage outcome="failure" />} />
        </Route>
      </Route>

      <Route element={<ProtectedRoute roles={['OFFICER']} />}>
        <Route element={<AppLayout />}>
          <Route path="/officer/dashboard" element={<OfficerDashboard />} />
          <Route path="/officer/security" element={<SecurityPage />} />
        </Route>
      </Route>

      <Route element={<ProtectedRoute roles={['ADMIN', 'OFFICER']} />}>
        <Route element={<StaffLayout />}>
          <Route
            path="/admin/scanner"
            element={
              <Suspense fallback={<Spinner label="Loading scanner…" />}>
                <ScannerPage />
              </Suspense>
            }
          />
        </Route>
      </Route>

      <Route element={<ProtectedRoute roles={['ADMIN']} />}>
        <Route element={<AdminLayout />}>
          <Route path="/admin/dashboard" element={<AdminDashboard />} />
          <Route path="/admin/applications" element={<AdminApplicationsPage />} />
          <Route path="/admin/applications/:id" element={<AdminApplicationReviewPage />} />
          <Route path="/admin/tourists" element={<AdminTouristsPage />} />
          <Route path="/admin/tourists/:id" element={<AdminTouristDetailPage />} />
          <Route path="/admin/permits" element={<AdminPermitsPage />} />
          <Route path="/admin/permits/:id" element={<AdminPermitDetailPage />} />
          <Route path="/admin/verifications" element={<VerificationsPage />} />
          <Route path="/admin/roads" element={<RoadConditionsPage />} />
          <Route path="/admin/areas" element={<AdminAreasPage />} />
          <Route path="/admin/audit" element={<AuditLogPage />} />
          <Route path="/admin/payments" element={<PaymentsPage />} />
          <Route path="/admin/staff" element={<StaffPage />} />
          <Route path="/admin/security" element={<SecurityPage />} />
        </Route>
      </Route>

      <Route path="*" element={<Home />} />
    </Routes>
    </Suspense>
  );
}
