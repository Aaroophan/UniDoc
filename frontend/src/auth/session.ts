import type { LoginResponse, UserSummary } from '../api/contracts'
import { isRecord, isUserSummary } from '../api/contracts'

/** Each tab owns its session: never switch this to localStorage or a cookie. */
export const SESSION_STORAGE_KEY = 'unidoc.auth.session.v1'
export const SESSION_ENDED_EVENT = 'unidoc:session-ended'

export type SessionEndReason = 'expired' | 'inactive' | 'signed-out'

export interface AuthSession {
  version: 1
  token: string
  expiresAt: string
  user: UserSummary
}

interface SessionEndedDetail {
  reason: SessionEndReason
}

function hasSessionStorage(): boolean {
  return typeof window !== 'undefined' && typeof window.sessionStorage !== 'undefined'
}

function parseSession(raw: string | null): AuthSession | null {
  if (!raw) return null
  try {
    const value: unknown = JSON.parse(raw)
    if (!isRecord(value)) return null
    if (value.version !== 1 || typeof value.token !== 'string' || value.token.length < 20 || value.token.length > 8192) return null
    if (typeof value.expiresAt !== 'string' || !Number.isFinite(Date.parse(value.expiresAt))) return null
    if (!isUserSummary(value.user)) return null
    return {
      version: 1,
      token: value.token,
      expiresAt: value.expiresAt,
      user: value.user,
    }
  } catch {
    return null
  }
}

function rawSession(): string | null {
  try {
    return hasSessionStorage() ? window.sessionStorage.getItem(SESSION_STORAGE_KEY) : null
  } catch {
    return null
  }
}

function isExpired(session: AuthSession): boolean {
  return Date.parse(session.expiresAt) <= Date.now()
}

/** A validated session, never an untrusted JSON object or expired token. */
export function getStoredSession(): AuthSession | null {
  const raw = rawSession()
  if (!raw) return null
  const parsed = parseSession(raw)
  if (!parsed) {
    removeStoredSession()
    return null
  }
  if (isExpired(parsed)) {
    endSession('expired', parsed.token)
    return null
  }
  return parsed
}

function removeStoredSession(): void {
  try {
    if (hasSessionStorage()) window.sessionStorage.removeItem(SESSION_STORAGE_KEY)
  } catch {
    // Private browsing/storage restrictions should not crash the UI.
  }
}

/** Used after a successful login or an authoritative /me verification. */
export function saveStoredSession(response: LoginResponse): AuthSession {
  const next: AuthSession = {
    version: 1,
    token: response.token,
    expiresAt: response.expiresAt,
    user: response.user,
  }
  if (response.tokenType !== 'Bearer' || !parseSession(JSON.stringify(next)) || isExpired(next)) {
    throw new Error('Invalid authentication response.')
  }
  if (!hasSessionStorage()) throw new Error('Session storage is unavailable.')
  window.sessionStorage.setItem(SESSION_STORAGE_KEY, JSON.stringify(next))
  return next
}

export function updateStoredUser(user: UserSummary, expectedToken: string): void {
  const current = getStoredSession()
  if (!current || current.token !== expectedToken) return
  try {
    window.sessionStorage.setItem(SESSION_STORAGE_KEY, JSON.stringify({ ...current, user }))
  } catch {
    // The in-memory current user is still authoritative for this tab.
  }
}

/** Prevent a late response from an older token signing out a newly signed-in user. */
export function endSession(reason: SessionEndReason, expectedToken?: string): void {
  if (expectedToken) {
    const current = parseSession(rawSession())
    if (!current || current.token !== expectedToken) return
  }
  removeStoredSession()
  if (typeof window !== 'undefined') {
    const detail: SessionEndedDetail = { reason }
    window.dispatchEvent(new CustomEvent<SessionEndedDetail>(SESSION_ENDED_EVENT, { detail }))
  }
}

export function sessionEndReason(event: Event): SessionEndReason | null {
  if (!(event instanceof CustomEvent) || !isRecord(event.detail)) return null
  const reason = event.detail.reason
  return reason === 'expired' || reason === 'inactive' || reason === 'signed-out' ? reason : null
}
