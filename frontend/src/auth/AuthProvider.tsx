import { useCallback, useEffect, useMemo, useState } from 'react'
import type { ReactNode } from 'react'
import { isUserSummary } from '../api/contracts'
import { getCurrentUser, loginUser } from './authApi'
import { AuthContext } from './AuthContext'
import type { AuthContextValue, AuthNotice, AuthStatus } from './AuthContext'
import {
  SESSION_ENDED_EVENT,
  endSession,
  getStoredSession,
  saveStoredSession,
  sessionEndReason,
  updateStoredUser,
} from './session'
import { Brand } from '../components/Brand'

interface AuthState {
  status: AuthStatus
  user: AuthContextValue['user']
  notice: AuthNotice
}

const initialState: AuthState = { status: 'initializing', user: null, notice: null }

export function AuthProvider({ children }: { children: ReactNode }) {
  const [state, setState] = useState<AuthState>(initialState)
  const [verificationAttempt, setVerificationAttempt] = useState(0)

  useEffect(() => {
    let active = true
    const controller = new AbortController()

    const onSessionEnded = (event: Event) => {
      const reason = sessionEndReason(event)
      if (reason) setState({ status: 'unauthenticated', user: null, notice: reason })
    }
    window.addEventListener(SESSION_ENDED_EVENT, onSessionEnded)

    async function restore() {
      const saved = getStoredSession()
      if (!saved) {
        if (active) setState((previous) => previous.status === 'unauthenticated' ? previous : {
          status: 'unauthenticated', user: null, notice: null,
        })
        return
      }
      try {
        const confirmed = await getCurrentUser({ signal: controller.signal })
        if (!active) return
        const latest = getStoredSession()
        if (!latest || latest.token !== saved.token) return
        if (!isUserSummary(confirmed) || confirmed.id !== saved.user.id || confirmed.role !== saved.user.role || confirmed.email !== saved.user.email || confirmed.status !== 'ACTIVE') {
          endSession('expired', saved.token)
          return
        }
        updateStoredUser(confirmed, saved.token)
        setState({ status: 'authenticated', user: confirmed, notice: null })
      } catch {
        if (!active || controller.signal.aborted) return
        // Token rejections are handled centrally in the Axios interceptor. A
        // network outage must not admit the user or silently destroy a valid session.
        if (getStoredSession()?.token === saved.token) {
          setState({ status: 'verification-error', user: null, notice: null })
        } else {
          setState({ status: 'unauthenticated', user: null, notice: 'expired' })
        }
      }
    }
    void restore()
    return () => {
      active = false
      controller.abort()
      window.removeEventListener(SESSION_ENDED_EVENT, onSessionEnded)
    }
  }, [verificationAttempt])

  const login = useCallback(async (input: Parameters<AuthContextValue['login']>[0]) => {
    const response = await loginUser(input)
    const saved = saveStoredSession(response)
    setState({ status: 'authenticated', user: saved.user, notice: null })
    return saved.user
  }, [])

  /** Available for later profile/account mutations that change public user details. */
  const refreshCurrentUser = useCallback(async () => {
    const current = getStoredSession()
    if (!current) return null
    const confirmed = await getCurrentUser()
    if (!isUserSummary(confirmed) || confirmed.id !== current.user.id || confirmed.role !== current.user.role || confirmed.email !== current.user.email || confirmed.status !== 'ACTIVE') {
      endSession('expired', current.token)
      return null
    }
    const latest = getStoredSession()
    if (!latest || latest.token !== current.token) return null
    updateStoredUser(confirmed, current.token)
    setState({ status: 'authenticated', user: confirmed, notice: null })
    return confirmed
  }, [])

  const logout = useCallback(() => {
    endSession('signed-out')
    setState({ status: 'unauthenticated', user: null, notice: 'signed-out' })
  }, [])

  const clearNotice = useCallback(() => {
    setState((previous) => previous.notice === null ? previous : { ...previous, notice: null })
  }, [])

  const retryVerification = useCallback(() => {
    setState(initialState)
    setVerificationAttempt((value) => value + 1)
  }, [])

  const value = useMemo<AuthContextValue>(() => ({
    status: state.status,
    user: state.user,
    notice: state.notice,
    login,
    refreshCurrentUser,
    logout,
    clearNotice,
    retryVerification,
  }), [state, login, refreshCurrentUser, logout, clearNotice, retryVerification])

  return (
    <AuthContext.Provider value={value}>
      {state.status === 'initializing' ? (
        <div className="session-gate" role="status" aria-live="polite">
          <Brand subtitle />
          <span className="spinner-border spinner-border-sm text-brand" aria-hidden="true" />
          <p>Checking your session...</p>
        </div>
      ) : state.status === 'verification-error' ? (
        <div className="session-gate" role="alert">
          <Brand subtitle />
          <div className="session-gate-card">
            <h1 className="h4">Could not verify your session</h1>
            <p className="text-muted mb-4">UniDoc could not reach the server. Your account has not been signed out.</p>
            <div className="d-flex flex-wrap justify-content-center gap-2">
              <button className="btn btn-primary" type="button" onClick={retryVerification}>Try again</button>
              <button className="btn btn-outline-secondary" type="button" onClick={logout}>Sign out</button>
            </div>
          </div>
        </div>
      ) : children}
    </AuthContext.Provider>
  )
}
