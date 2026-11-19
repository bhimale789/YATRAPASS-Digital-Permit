import { useEffect, useState } from 'react';
import { NavLink, Outlet, useLocation, useNavigate } from 'react-router-dom';
import { Icon, type IconName } from '../components/Icon';
import { useAuth } from '../hooks/useAuth';

interface NavItem {
  to: string;
  label: string;
  short?: string;
  icon: IconName;
  /** Shown in the mobile bottom tab bar. */
  primary?: boolean;
}

const NAV: NavItem[] = [
  { to: '/admin/dashboard', label: 'Dashboard', short: 'Home', icon: 'chart', primary: true },
  { to: '/admin/applications', label: 'Applications', short: 'Review', icon: 'doc', primary: true },
  { to: '/admin/scanner', label: 'QR Scanner', short: 'Scan', icon: 'scan', primary: true },
  { to: '/admin/permits', label: 'Permits', icon: 'ticket', primary: true },
  { to: '/admin/tourists', label: 'Tourists', icon: 'users' },
  { to: '/admin/payments', label: 'Payments', icon: 'wallet' },
  { to: '/admin/verifications', label: 'Verifications', icon: 'history' },
  { to: '/admin/roads', label: 'Road Conditions', icon: 'road' },
  { to: '/admin/areas', label: 'Areas', icon: 'map' },
  { to: '/admin/staff', label: 'Staff', icon: 'badge' },
  { to: '/admin/audit', label: 'Audit Log', icon: 'shield' },
  { to: '/admin/security', label: 'My Security', icon: 'key' },
];

/**
 * Administrator shell: fixed sidebar on desktop; on phones a top
 * bar with a slide-in drawer and a bottom tab bar.
 */
export function AdminLayout() {
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [drawerOpen, setDrawerOpen] = useState(false);
  const primary = NAV.filter((n) => n.primary);
  const current = NAV.find((i) => location.pathname.startsWith(i.to));

  useEffect(() => setDrawerOpen(false), [location.pathname]);
  useEffect(() => {
    if (!drawerOpen) return;
    const onKey = (e: KeyboardEvent) => e.key === 'Escape' && setDrawerOpen(false);
    document.addEventListener('keydown', onKey);
    document.body.classList.add('no-scroll');
    return () => {
      document.removeEventListener('keydown', onKey);
      document.body.classList.remove('no-scroll');
    };
  }, [drawerOpen]);

  const onLogout = async () => {
    await logout();
    navigate('/login', { replace: true });
  };

  return (
    <div className="shell has-tabbar">
      <header className="topbar">
        <button className="icon-btn" aria-label="Open menu" aria-expanded={drawerOpen} onClick={() => setDrawerOpen(true)}>
          <Icon name="menu" />
        </button>
        <span className="topbar-title">{current?.label ?? 'YatraPass'}</span>
        <img src="/favicon.svg" alt="" width={28} height={28} />
      </header>

      <div className={`backdrop ${drawerOpen ? 'show' : ''}`} onClick={() => setDrawerOpen(false)} aria-hidden />

      <aside className={`sidebar ${drawerOpen ? 'open' : ''}`} aria-label="Main navigation">
        <div className="brand">
          <img src="/favicon.svg" alt="" width={32} height={32} />
          <div>
            <strong>YatraPass</strong>
            <small>Nepal · Digital Tourist Permits</small>
          </div>
          <button className="icon-btn drawer-close" aria-label="Close menu" onClick={() => setDrawerOpen(false)}>
            <Icon name="close" />
          </button>
        </div>
        <nav className="nav">
          {NAV.map((item) => (
            <NavLink key={item.to} to={item.to} className={({ isActive }) => (isActive ? 'active' : '')}>
              <Icon name={item.icon} size={20} />
              {item.label}
            </NavLink>
          ))}
        </nav>
        <div className="sidebar-footer">
          <div className="user-chip" title={user?.email}>
            <span className="user-email">{user?.email}</span>
            <span className="role-tag">{user?.role}</span>
          </div>
          <button className="btn btn-ghost btn-sm" onClick={onLogout}>
            <Icon name="logout" size={18} /> Log out
          </button>
        </div>
      </aside>

      <main className="content">
        <Outlet />
      </main>

      <nav className="tabbar" aria-label="Quick navigation">
        {primary.map((item) => (
          <NavLink key={item.to} to={item.to} className={({ isActive }) => (isActive ? 'active' : '')}>
            <Icon name={item.icon} />
            <span>{item.short ?? item.label}</span>
          </NavLink>
        ))}
      </nav>
    </div>
  );
}
