import { Navigate, Outlet, useLocation } from 'react-router-dom'
import type { Location } from 'react-router-dom'
import type { UserRole } from '../api/contracts'
import { useAuth } from './AuthContext'

export interface IntendedLocation {
  pathname: string
  search: string
}

/** Use only internal paths for redirect intent, never URLs provided by a caller. */
export function safeIntendedLocation(value: unknown): string | null {
  if (typeof value !== 'object' || value === null || !('from' in value)) return null
  const from = value.from
  if (typeof from !== 'object' || from === null || !('pathname' in from) || !('search' in from)) return null
  if (typeof from.pathname !== 'string' || typeof from.search !== 'string') return null
  if (!from.pathname.startsWith('/') || from.pathname.startsWith('//') || from.pathname.includes('\\')) return null
  if (from.pathname === '/login' || from.pathname === '/register') return null
  if (from.pathname.includes('\r') || from.pathname.includes('\n')) return null
  if (from.search.length > 512 || (from.search && !from.search.startsWith('?'))) return null
  return from.pathname + from.search
}

export function roleHome(role: UserRole): string {
  switch (role) {
    case 'STUDENT': return '/requests'
    case 'STAFF': return '/staff/requests'
    case 'ADMIN': return '/admin/dashboard'
  }
}

function intendedLocation(location: Location): IntendedLocation {
  return { pathname: location.pathname, search: location.search }
}

export function ProtectedRoute() {
  const { user } = useAuth()
  const location = useLocation()
  if (!user) return <Navigate to="/login" replace state={{ from: intendedLocation(location) }} />
  return <Outlet />
}

export function RoleRoute({ allowedRoles }: { allowedRoles: readonly UserRole[] }) {
  const { user } = useAuth()
  if (!user) return <Navigate to="/login" replace />
  if (!allowedRoles.includes(user.role)) {
    return <Navigate to={roleHome(user.role)} replace />
  }
  return <Outlet />
}

export function PublicOnlyRoute() {
  const { user } = useAuth()
  if (user) return <Navigate to={roleHome(user.role)} replace />
  return <Outlet />
}

export function DashboardRedirect() {
  const { user } = useAuth()
  if (!user) return <Navigate to="/login" replace />
  return <Navigate to={roleHome(user.role)} replace />
}
