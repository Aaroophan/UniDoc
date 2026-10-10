import { createContext, useContext } from 'react'
import type { LoginRequest, UserSummary } from '../api/contracts'

export type AuthStatus = 'initializing' | 'authenticated' | 'unauthenticated' | 'verification-error'
export type AuthNotice = 'expired' | 'inactive' | 'signed-out' | null

export interface AuthContextValue {
  status: AuthStatus
  user: UserSummary | null
  notice: AuthNotice
  login: (input: LoginRequest) => Promise<UserSummary>
  refreshCurrentUser: () => Promise<UserSummary | null>
  logout: () => void
  clearNotice: () => void
  retryVerification: () => void
}

export const AuthContext = createContext<AuthContextValue | null>(null)

export function useAuth(): AuthContextValue {
  const auth = useContext(AuthContext)
  if (!auth) throw new Error('AuthContext must be used within AuthProvider.')
  return auth
}
