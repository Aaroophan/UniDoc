import { Navigate, Outlet, useLocation } from 'react-router-dom'
import type { Location } from 'react-router-dom'
import type { UserRole } from '../api/contracts'
import { roleHome } from './routing'
import type { IntendedLocation } from './routing'
import { useAuth } from './AuthContext'

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
