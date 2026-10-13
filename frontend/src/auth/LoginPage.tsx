import { useRef, useState } from 'react'
import type { FormEvent } from 'react'
import { Link, useLocation, useNavigate } from 'react-router-dom'
import type { FieldErrors } from '../api/contracts'
import { getApiError, getSafeErrorMessage } from '../api/errors'
import { FeedbackBanner } from '../components/FeedbackBanner'
import { focusFirstInvalidField } from '../components/formFocus'
import type { FeedbackVariant } from '../components/FeedbackBanner'
import { useAuth } from './AuthContext'
import { roleHome, safeIntendedLocation } from './routing'
import { normalizeEmail, validateLogin } from './validation'

interface Feedback {
  variant: FeedbackVariant
  message: string
  requestId?: string | null
}

function registeredEmail(value: unknown): string {
  if (typeof value !== 'object' || value === null || !('registeredEmail' in value)) return ''
  const candidate = value.registeredEmail
  return typeof candidate === 'string' && candidate.length <= 254 ? candidate : ''
}

export function LoginPage() {
  const location = useLocation()
  const navigate = useNavigate()
  const { login, notice, clearNotice } = useAuth()
  const [email, setEmail] = useState(() => registeredEmail(location.state))
  const [password, setPassword] = useState('')
  const [fieldErrors, setFieldErrors] = useState<FieldErrors>({})
  const [feedback, setFeedback] = useState<Feedback | null>(null)
  const [submitting, setSubmitting] = useState(false)
  const formRef = useRef<HTMLFormElement>(null)

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (submitting) return
    setFeedback(null)
    clearNotice()
    const errors = validateLogin({ email, password })
    setFieldErrors(errors)
    if (Object.keys(errors).length > 0) {
      focusFirstInvalidField(formRef.current, Object.keys(errors))
      return
    }

    setSubmitting(true)
    try {
      const user = await login({ email: normalizeEmail(email), password })
      setPassword('')
      navigate(safeIntendedLocation(location.state) ?? roleHome(user.role), { replace: true, state: null })
    } catch (error: unknown) {
      const apiError = getApiError(error)
      if (apiError?.fieldErrors) {
        setFieldErrors(apiError.fieldErrors)
        focusFirstInvalidField(formRef.current, Object.keys(apiError.fieldErrors))
      }
      let message = getSafeErrorMessage(error, 'Unable to sign in. Please try again.')
      if (apiError?.code === 'AUTH_INVALID_CREDENTIALS') message = 'Email or password is incorrect.'
      else if (apiError?.code === 'ACCOUNT_INACTIVE') message = 'This account is inactive. Contact an administrator.'
      setFeedback({ variant: 'error', message, requestId: apiError?.requestId })
      setPassword('') // Never retain a failed password in the UI.
    } finally {
      setSubmitting(false)
    }
  }

  const noticeMessage = notice === 'expired' ? 'Session expired. Please sign in again.'
    : notice === 'inactive' ? 'This account is inactive. Contact an administrator.'
      : notice === 'signed-out' ? 'You have signed out.' : null

  return (
    <div className="auth-form-card">
      <span className="auth-form-eyebrow">WELCOME BACK</span>
      <h1 className="auth-form-title">Sign in to UniDoc</h1>
      <p className="auth-form-intro">Access your document requests and stay on top of what comes next.</p>

      {noticeMessage && <div className="mb-4"><FeedbackBanner variant={notice === 'inactive' ? 'warning' : 'info'} message={noticeMessage} onDismiss={clearNotice} /></div>}
      {feedback && <div className="mb-4"><FeedbackBanner {...feedback} onDismiss={() => setFeedback(null)} /></div>}

      <form ref={formRef} onSubmit={(event) => void handleSubmit(event)} noValidate>
        <div className="form-field">
          <label className="form-label" htmlFor="login-email">Email address</label>
          <input
            className={`form-control${fieldErrors.email ? ' is-invalid' : ''}`}
            id="login-email" name="email" type="email" autoComplete="username"
            inputMode="email" placeholder="you@example.com" value={email}
            onChange={(event) => { setEmail(event.target.value); setFieldErrors((previous) => ({ ...previous, email: '' })) }}
            aria-invalid={Boolean(fieldErrors.email)} aria-describedby={fieldErrors.email ? 'login-email-error' : undefined}
            disabled={submitting}
          />
          {fieldErrors.email && <div id="login-email-error" className="invalid-feedback">{fieldErrors.email}</div>}
        </div>
        <div className="form-field">
          <label className="form-label" htmlFor="login-password">Password</label>
          <input
            className={`form-control${fieldErrors.password ? ' is-invalid' : ''}`}
            id="login-password" name="password" type="password" autoComplete="current-password"
            placeholder="Enter your password" value={password}
            onChange={(event) => { setPassword(event.target.value); setFieldErrors((previous) => ({ ...previous, password: '' })) }}
            aria-invalid={Boolean(fieldErrors.password)} aria-describedby={fieldErrors.password ? 'login-password-error' : undefined}
            disabled={submitting}
          />
          {fieldErrors.password && <div id="login-password-error" className="invalid-feedback">{fieldErrors.password}</div>}
        </div>
        <button className="btn btn-primary btn-auth-submit w-100" type="submit" disabled={submitting}>
          {submitting ? <><span className="spinner-border spinner-border-sm me-2" aria-hidden="true" />Signing in...</> : 'Sign in'}
        </button>
      </form>
      <div className="auth-form-divider" />
      <p className="auth-form-switch">New to UniDoc? <Link to="/register">Create a student account</Link></p>
      <p className="auth-form-footnote">Staff and administrators sign in with accounts created for them by an administrator.</p>
    </div>
  )
}
