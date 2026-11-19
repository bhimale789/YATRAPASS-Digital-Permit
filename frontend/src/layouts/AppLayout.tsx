import { useEffect, useRef, useState, type FormEvent } from 'react';
import { Link, NavLink, Outlet, useLocation, useNavigate } from 'react-router-dom';
import { Icon, type IconName } from '../components/Icon';
import { homePathFor, useAuth } from '../hooks/useAuth';
import type { Role } from '../types';

interface NavItem {
  to: string;
  label: string;
  short?: string;
  icon: IconName;
  roles: Role[];
  /** Shown as a link in the desktop top bar (others go under "More"). */
  top?: boolean;
  /** Shown in the mobile bottom tab bar. */
  tab?: boolean;
}

const NAV: NavItem[] = [
  { to: '/officer/dashboard', label: 'Dashboard', short: 'Home', icon: 'home', roles: ['OFFICER'], top: true, tab: true },
  { to: '/admin/dashboard', label: 'Dashboard', short: 'Home', icon: 'chart', roles: ['ADMIN'], top: true, tab: true },
  { to: '/admin/applications', label: 'Applications', short: 'Review', icon: 'doc', roles: ['ADMIN'], top: true, tab: true },
  { to: '/admin/scanner', label: 'Scanner', short: 'Scan', icon: 'scan', roles: ['ADMIN', 'OFFICER'], tab: true },
  { to: '/admin/permits', label: 'Permits', icon: 'ticket', roles: ['ADMIN'], top: true, tab: true },
  { to: '/admin/tourists', label: 'Tourists', icon: 'users', roles: ['ADMIN'], top: true },
  { to: '/admin/verifications', label: 'Verifications', icon: 'history', roles: ['ADMIN'], top: true },
  { to: '/admin/areas', label: 'Areas', icon: 'map', roles: ['ADMIN'] },
  { to: '/admin/audit', label: 'Audit Log', icon: 'shield', roles: ['ADMIN'] },
  { to: '/officer/security', label: 'Security', icon: 'key', roles: ['OFFICER'] },
];

/** Hook: closes a popup on outside click / Escape. */
function usePopup() {
  const [open, setOpen] = useState(false);
  const ref = useRef<HTMLDivElement>(null);
  useEffect(() => {
    if (!open) return;
    const click = (e: MouseEvent) => !ref.current?.contains(e.target as Node) && setOpen(false);
    const key = (e: KeyboardEvent) => e.key === 'Escape' && setOpen(false);
    document.addEventListener('mousedown', click);
    document.addEventListener('keydown', key);
    return () => {
      document.removeEventListener('mousedown', click);
      document.removeEventListener('keydown', key);
    };
  }, [open]);
  return { open, setOpen, ref };
}

/**
 * Staff (ADMIN / OFFICER) shell in the same style as the tourist experience:
 * top navigation on desktop, compact header + bottom tab bar on phones.
 */
export function AppLayout() {
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const more = usePopup();
  const account = usePopup();
  const [query, setQuery] = useState('');

  const isAdmin = user?.role === 'ADMIN';
  const items = NAV.filter((n) => user && n.roles.includes(user.role));
  const topItems = items.filter((n) => n.top);
  const moreItems = items.filter((n) => !n.top && n.to !== '/admin/scanner');
  const tabItems = items.filter((n) => n.tab);
  const moreActive = moreItems.some((n) => location.pathname.startsWith(n.to));

  useEffect(() => {
    more.setOpen(false);
    account.setOpen(false);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [location.pathname]);

  const search = (e: FormEvent) => {
    e.preventDefault();
    const q = query.trim();
    navigate(q ? `/admin/permits?q=${encodeURIComponent(q)}` : '/admin/permits');
  };

  const onLogout = async () => {
    await logout();
    navigate('/login', { replace: true });
  };

  return (
    <div className="t-shell staff-shell">
      <header className="t-header">
        <Link to={homePathFor(user)} className="t-logo" aria-label="Home">
          <span className="t-logo-mark"><Icon name="shield" size={18} /></span>
          <span className="t-logo-text">permits<span>.np</span></span>
          <span className="role-pill">{isAdmin ? 'Admin' : 'Officer'}</span>
        </Link>

        {isAdmin && (
          <form className="t-search staff-search" role="search" onSubmit={search}>
            <span className="t-search-icon" aria-hidden>⌕</span>
            <input type="search" placeholder="Search permit no., tourist, passport…" aria-label="Search permits"
              value={query} onChange={(e) => setQuery(e.target.value)} maxLength={100} />
          </form>
        )}

        <nav className="t-nav" aria-label="Main">
          {topItems.map((n) => (
            <NavLink key={n.to} to={n.to} className={({ isActive }) => (isActive ? 'active' : '')}>{n.label}</NavLink>
          ))}
          {moreItems.length > 0 && (
            <div className="t-more" ref={more.ref}>
              <button className={`t-more-btn ${moreActive ? 'active' : ''}`} aria-haspopup="menu" aria-expanded={more.open}
                onClick={() => more.setOpen((o) => !o)}>
                More <span aria-hidden>▾</span>
              </button>
              {more.open && (
                <div className="t-menu t-menu-left" role="menu">
                  {moreItems.map((n) => (
                    <Link key={n.to} role="menuitem" to={n.to}><Icon name={n.icon} size={18} /> {n.label}</Link>
                  ))}
                </div>
              )}
            </div>
          )}
        </nav>

        <Link to="/admin/scanner" className="t-new-btn">
          <Icon name="scan" size={20} /> Scan QR
        </Link>

        <div className="t-account" ref={account.ref}>
          <button className="t-avatar-btn" aria-haspopup="menu" aria-expanded={account.open} onClick={() => account.setOpen((o) => !o)}>
            <span className="t-avatar">{(user?.email ?? '?').charAt(0).toUpperCase()}</span>
            <span className="t-caret" aria-hidden>▾</span>
          </button>
          {account.open && (
            <div className="t-menu" role="menu">
              <div className="t-menu-user">
                {user?.email}
                <span className="role-pill small-pill">{user?.role}</span>
              </div>
              {/* On phones the full navigation lives here as well. */}
              <div className="t-menu-mobile">
                {items.map((n) => (
                  <Link key={n.to} role="menuitem" to={n.to}><Icon name={n.icon} size={18} /> {n.label}</Link>
                ))}
              </div>
              <button role="menuitem" onClick={onLogout}><Icon name="logout" size={18} /> Log out</button>
            </div>
          )}
        </div>
      </header>

      <main className="t-main staff-main">
        <Outlet />
      </main>

      <nav className="tabbar t-tabbar" aria-label="Quick navigation">
        {tabItems.map((n) => (
          <NavLink key={n.to} to={n.to} className={({ isActive }) => (isActive ? 'active' : '')}>
            <Icon name={n.icon} />
            <span>{n.short ?? n.label}</span>
          </NavLink>
        ))}
        {isAdmin && (
          <button className={`tab-more ${moreActive ? 'active' : ''}`} onClick={() => account.setOpen(true)}>
            <Icon name="menu" />
            <span>More</span>
          </button>
        )}
      </nav>
    </div>
  );
}
