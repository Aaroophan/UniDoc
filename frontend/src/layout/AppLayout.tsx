import { useState } from 'react'
import { NavLink, Outlet, useLocation, useNavigate } from 'react-router-dom'
import type { UserRole } from '../api/contracts'
import { ROLE_LABELS } from '../api/contracts'
import { useAuth } from '../auth/AuthContext'
import { roleHome } from '../auth/routing'
import { Brand } from '../components/Brand'
import { NotificationCountContext } from '../notifications/NotificationCountContext'
import { useNotificationCountState } from '../notifications/useNotificationCountState'
import '../notifications/notifications.css'

interface NavItem {
  label: string
  to: string
  end?: boolean
}

const NAV_ITEMS: Record<UserRole, NavItem[]> = {
  STUDENT: [
    { label: 'My requests', to: '/requests' },
    { label: 'Document catalogue', to: '/catalogue' },
    { label: 'My profile', to: '/profile' },
  ],
  STAFF: [
    { label: 'Request work list', to: '/staff/requests' },
    { label: 'Document catalogue', to: '/catalogue' },
  ],
  ADMIN: [
    { label: 'Overview', to: '/admin/dashboard' },
    { label: 'Account management', to: '/admin/users' },
    { label: 'Document types', to: '/admin/document-types' },
    { label: 'Monitor requests', to: '/admin/requests' },
    { label: 'Reports', to: '/admin/reports' },
    { label: 'Document catalogue', to: '/catalogue' },
  ],
}

function NavigationItem({ item, close, unreadCount }: { item: NavItem; close: () => void; unreadCount?: number | null }) {
  return (
    <NavLink
      to={item.to}
      end={item.end}
      onClick={close}
      className={({ isActive }) => `side-nav-link${isActive ? ' active' : ''}`}
    >
      <span className="side-nav-indicator" aria-hidden="true" />
      {item.label}
      {item.to === '/notifications' && typeof unreadCount === 'number' && unreadCount > 0 && (
        <span className="notification-nav-count" aria-label={`${unreadCount} unread notifications`}>{unreadCount}</span>
      )}
    </NavLink>
  )
}

export function AppLayout() {
  const { user, logout } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const [openAtPath, setOpenAtPath] = useState<string | null>(null)
  const notificationCount = useNotificationCountState(user?.id ?? null)
  const navOpen = openAtPath === location.pathname
  if (!user) return null // ProtectedRoute is the authoritative UI guard.

  function signOut() {
    logout()
    navigate('/login', { replace: true, state: null })
  }
  const roleName = ROLE_LABELS[user.role]

  return (
    <NotificationCountContext.Provider value={notificationCount}>
    <div className="app-shell">
      <a className="skip-to-content" href="#main-content">Skip to content</a>
      <header className="app-topbar">
        <div className="topbar-inner">
          <button
            type="button"
            className="menu-toggle"
            aria-label={navOpen ? 'Close navigation' : 'Open navigation'}
            aria-controls="primary-navigation"
            aria-expanded={navOpen}
            onClick={() => setOpenAtPath((current) => current === location.pathname ? null : location.pathname)}
          >
            <span aria-hidden="true" className="menu-lines">☰</span>
          </button>
          <Brand compact />
          <div className="topbar-links">
            <NavLink to="/notifications" aria-label={notificationCount.unreadCount ? `Notifications, ${notificationCount.unreadCount} unread` : "Notifications"} className={({ isActive }) => `topbar-link${isActive ? ' active' : ''}`}>Notifications{notificationCount.unreadCount !== null && notificationCount.unreadCount > 0 && <span className="notification-nav-count">{notificationCount.unreadCount}</span>}</NavLink>
            <NavLink to="/account" className={({ isActive }) => `topbar-link${isActive ? ' active' : ''}`}>My account</NavLink>
            <button type="button" className="topbar-sign-out" onClick={signOut}>Sign out</button>
          </div>
          <NavLink className="mobile-account-link" to="/account" aria-label="My account">
            <span aria-hidden="true">{user.fullName.trim().charAt(0).toUpperCase()}</span>
          </NavLink>
        </div>
      </header>
      <div className="app-body">
        <aside className={`app-sidebar${navOpen ? ' is-open' : ''}`} id="primary-navigation">
          <div className="sidebar-body">
            <p className="sidebar-section-label">{roleName} workspace</p>
            <nav className="side-nav" aria-label="Main navigation">
              {NAV_ITEMS[user.role].map((item) => <NavigationItem key={item.to} item={item} close={() => setOpenAtPath(null)} />)}
            </nav>
            <div className="sidebar-divider" />
            <p className="sidebar-section-label">Your space</p>
            <nav className="side-nav" aria-label="Personal navigation">
              <NavigationItem item={{ label: 'Notifications', to: '/notifications' }} unreadCount={notificationCount.unreadCount} close={() => setOpenAtPath(null)} />
              <NavigationItem item={{ label: 'Account settings', to: '/account' }} close={() => setOpenAtPath(null)} />
            </nav>
            <button type="button" className="mobile-nav-sign-out" onClick={signOut}>Sign out</button>
          </div>
          <div className="sidebar-bottom">
            <div className="sidebar-avatar" aria-hidden="true">{user.fullName.trim().charAt(0).toUpperCase()}</div>
            <div className="sidebar-user-text">
              <strong title={user.fullName}>{user.fullName}</strong>
              <small>{roleName}</small>
            </div>
          </div>
        </aside>
        <div className="app-main-area">
          <div className="app-main-inner">
            <div className="content-breadcrumb">
              <span className="content-breadcrumb-app">UniDoc</span>
              <span className="content-breadcrumb-separator" aria-hidden="true">/</span>
              <span>{roleName} workspace</span>
            </div>
            <main id="main-content" tabIndex={-1} className="app-content"><Outlet /></main>
          </div>
          <footer className="app-footer d-flex flex-wrap justify-content-between gap-2">
            <span>UniDoc · University Document Request Management System</span>
            <span><a href={roleHome(user.role)}>Workspace home</a></span>
          </footer>
        </div>
      </div>
    </div>
    </NotificationCountContext.Provider>
  )
}
