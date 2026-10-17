import { useEffect, useState } from 'react';
import { NavLink, Outlet, useLocation } from 'react-router-dom';
import {
  BarChart3,
  CalendarClock,
  Link2,
  LogOut,
  Menu,
  MessageSquare,
  Moon,
  Radar,
  Settings,
  Sun,
} from 'lucide-react';
import { useAuth } from '../auth/AuthContext';

const NAV = [
  { to: '/', label: 'Dashboard', icon: BarChart3 },
  { to: '/publisher', label: 'Publisher', icon: CalendarClock },
  { to: '/engagement', label: 'Engagement', icon: MessageSquare },
  { to: '/listening', label: 'Listening', icon: Radar },
  { to: '/accounts', label: 'Accounts', icon: Link2 },
  { to: '/settings', label: 'Settings', icon: Settings },
];

type Theme = 'light' | 'dark';

function initialTheme(): Theme {
  try {
    const saved = localStorage.getItem('socialdash.theme');
    if (saved === 'light' || saved === 'dark') return saved;
  } catch {
    /* ignore */
  }
  return window.matchMedia?.('(prefers-color-scheme: dark)').matches ? 'dark' : 'light';
}

export function BrandMark() {
  return (
    <div className="brand">
      <div className="brand-mark">
        <BarChart3 size={17} />
      </div>
      SocialDash
    </div>
  );
}

export default function Layout() {
  const { user, logout } = useAuth();
  const [theme, setTheme] = useState<Theme>(initialTheme);
  const [menuOpen, setMenuOpen] = useState(false);
  const location = useLocation();

  useEffect(() => {
    document.documentElement.dataset.theme = theme;
    try {
      localStorage.setItem('socialdash.theme', theme);
    } catch {
      /* ignore */
    }
  }, [theme]);

  useEffect(() => setMenuOpen(false), [location.pathname]);

  return (
    <div className="shell">
      <div className="mobile-bar">
        <BrandMark />
        <button className="icon-btn" onClick={() => setMenuOpen(true)} aria-label="Open menu">
          <Menu size={20} />
        </button>
      </div>
      <aside className={`sidebar ${menuOpen ? 'open' : ''}`} onClick={(e) => e.target === e.currentTarget && setMenuOpen(false)}>
        <BrandMark />
        {NAV.map(({ to, label, icon: Icon }) => (
          <NavLink key={to} to={to} end={to === '/'} className={({ isActive }) => `nav-link ${isActive ? 'active' : ''}`}>
            <Icon size={17} />
            {label}
          </NavLink>
        ))}
        <div className="sidebar-footer">
          <button className="nav-link btn-ghost" style={{ border: 'none', cursor: 'pointer', background: 'none' }}
                  onClick={() => setTheme(theme === 'dark' ? 'light' : 'dark')}>
            {theme === 'dark' ? <Sun size={17} /> : <Moon size={17} />}
            {theme === 'dark' ? 'Light mode' : 'Dark mode'}
          </button>
          <div className="user-chip">
            <div className="avatar">{user?.name.charAt(0).toUpperCase()}</div>
            <div className="meta">
              <div style={{ fontWeight: 600 }}>{user?.name}</div>
              <div className="muted small">{user?.email}</div>
            </div>
            <button className="icon-btn" onClick={logout} title="Sign out" aria-label="Sign out" style={{ marginLeft: 'auto' }}>
              <LogOut size={16} />
            </button>
          </div>
        </div>
      </aside>
      <main className="main">
        <Outlet />
      </main>
    </div>
  );
}
