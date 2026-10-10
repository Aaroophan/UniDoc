import type { UserRole } from '../api/contracts'

export interface IntendedLocation {
  pathname: string
  search: string
}

/** Only internal paths can be used as post-login destinations. */
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
