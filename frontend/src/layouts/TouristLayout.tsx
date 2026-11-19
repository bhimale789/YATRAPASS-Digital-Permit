import { useEffect, useRef, useState, type FormEvent } from 'react';
import { Link, NavLink, Outlet, useLocation, useNavigate, useSearchParams } from 'react-router-dom';
import { Icon, type IconName } from '../components/Icon';
import { NotificationBell } from '../components/NotificationBell';
import { useAuth } from '../hooks/useAuth';

const LINKS: { to: string; label: string; icon: IconName }[] = [
  { to: '/tourist/dashboard', label: 'Home', icon: 'home' },
  { to: '/tourist/explore', label: 'Explore', icon: 'map' },
  { to: '/tourist/applications', label: 'Applications', icon: 'doc' },
  { to: '/tourist/permits', label: 'Permits', icon: 'ticket' },
];

/** Tourist shell: horizontal top navigation (desktop) and bottom tab bar (phones). */
export function TouristLayout() {
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [params] = useSearchParams();
  const [query, setQuery] = useState(location.pathname === '/tourist/explore' ? params.get('q') ?? '' : '');
  const [menuOpen, setMenuOpen] = useState(false);
  const menuRef = useRef<HTMLDivElement>(null);

  useEffect(() => setMenuOpen(false), [location.pathname]);
  useEffect(() => {
    if (!menuOpen) return;
    const close = (e: MouseEvent) => {
      if (!menuRef.current?.contains(e.target as Node)) setMenuOpen(false);
    };
    const esc = (e: KeyboardEvent) => e.key === 'Escape' && setMenuOpen(false);
    document.addEventListener('mousedown', close);
    document.addEventListener('keydown', esc);
    return () => {
      document.removeEventListener('mousedown', close);
      document.removeEventListener('keydown', esc);
    };
  }, [menuOpen]);

  const search = (e: FormEvent) => {
    e.preventDefault();
    const q = query.trim();
    navigate(q ? `/tourist/explore?q=${encodeURIComponent(q)}` : '/tourist/explore');
  };

  const onLogout = async () => {
    await logout();
    navigate('/login', { replace: true });
  };

  const initial = (user?.email ?? '?').charAt(0).toUpperCase();

  return (
    <div className="t-shell">
      <header className="t-header">
        <Link to="/tourist/dashboard" className="t-logo" aria-label="YatraPass home">
          <span className="t-logo-mark"><Icon name="map" size={18} /></span>
          <span className="t-logo-text">yatra<span>pass</span></span>
        </Link>

        <form className="t-search" role="search" onSubmit={search}>
          <span className="t-search-icon" aria-hidden>⌕</span>
          <input
            type="search"
            placeholder="Search destinations, e.g. annapurna"
            aria-label="Search destinations"
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            maxLength={100}
          />
        </form>

        <nav className="t-nav" aria-label="Main">
          {LINKS.map((l) => (
            <NavLink key={l.to} to={l.to} className={({ isActive }) => (isActive ? 'active' : '')}>
              {l.label}
            </NavLink>
          ))}
        </nav>

        <Link to="/tourist/applications/new" className="t-new-btn">
          New <span className="t-new-plus" aria-hidden>+</span>
        </Link>

        <NotificationBell />

        <div className="t-account" ref={menuRef}>
          <button className="t-avatar-btn" aria-haspopup="menu" aria-expanded={menuOpen} onClick={() => setMenuOpen((o) => !o)}>
            <span className="t-avatar">{initial}</span>
            <span className="t-caret" aria-hidden>▾</span>
          </button>
          {menuOpen && (
            <div className="t-menu" role="menu">
              <div className="t-menu-user">{user?.email}</div>
              <Link role="menuitem" to="/tourist/profile"><Icon name="user" size={18} /> My profile</Link>
              <Link role="menuitem" to="/tourist/permits"><Icon name="ticket" size={18} /> My permits & QR codes</Link>
              <Link role="menuitem" to="/tourist/notifications"><Icon name="bell" size={18} /> Notifications</Link>
              <Link role="menuitem" to="/tourist/security"><Icon name="key" size={18} /> Password & security</Link>
              <button role="menuitem" onClick={onLogout}><Icon name="logout" size={18} /> Log out</button>
            </div>
          )}
        </div>
      </header>

      <main className="t-main">
        <Outlet />
      </main>

      <nav className="tabbar t-tabbar" aria-label="Quick navigation">
        {[...LINKS, { to: '/tourist/profile', label: 'Profile', icon: 'user' as IconName }].map((l) => (
          <NavLink key={l.to} to={l.to} className={({ isActive }) => (isActive ? 'active' : '')}>
            <Icon name={l.icon} />
            <span>{l.label}</span>
          </NavLink>
        ))}
      </nav>
    </div>
  );
}
