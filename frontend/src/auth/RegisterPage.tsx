import { useState } from 'react'
import type { FormEvent } from 'react'
import { Link } from 'react-router-dom'
import type { FieldErrors, RegisterRequest } from '../api/contracts'
import { getApiError, getSafeErrorMessage } from '../api/errors'
import { FeedbackBanner } from '../components/FeedbackBanner'
import { registerStudent } from './authApi'
import { normalizeEmail, validateRegistration } from './validation'

const emptyForm: RegisterRequest = { fullName: '', email: '', password: '', confirmPassword: '' }

export function RegisterPage() {
  const [form, setForm] = useState<RegisterRequest>(emptyForm)
  const [errors, setErrors] = useState<FieldErrors>({})
  const [message, setMessage] = useState<{ text: string; requestId?: string | null } | null>(null)
  const [successEmail, setSuccessEmail] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  function updateField(field: keyof RegisterRequest, value: string) {
    setForm((previous) => ({ ...previous, [field]: value }))
    setErrors((previous) => ({ ...previous, [field]: '' }))
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (submitting) return
    setMessage(null)
    const nextErrors = validateRegistration(form)
    setErrors(nextErrors)
    if (Object.keys(nextErrors).length > 0) return
    setSubmitting(true)
    try {
      const result = await registerStudent({
        fullName: form.fullName.trim(),
        email: normalizeEmail(form.email),
        password: form.password,
        confirmPassword: form.confirmPassword,
      })
      setSuccessEmail(result.email)
      setForm(emptyForm)
      setErrors({})
    } catch (error: unknown) {
      const apiError = getApiError(error)
      if (apiError?.fieldErrors) setErrors(apiError.fieldErrors)
      if (apiError?.code === 'EMAIL_ALREADY_EXISTS') {
        setErrors((previous) => ({ ...previous, email: 'An account with this email already exists.' }))
      }
      setMessage({
        text: apiError?.code === 'EMAIL_ALREADY_EXISTS'
          ? 'This email is already registered. Try signing in instead.'
          : getSafeErrorMessage(error, 'Registration failed. Please try again.'),
        requestId: apiError?.requestId,
      })
    } finally {
      setSubmitting(false)
    }
  }

  if (successEmail) {
    return (
      <section className="auth-form-card" role="status" aria-live="polite">
        <div className="registration-success-icon" aria-hidden="true">✓</div>
        <span className="auth-form-eyebrow">ACCOUNT CREATED</span>
        <h1 className="auth-form-title">You’re all set.</h1>
        <p className="auth-form-intro">Your student account <strong className="text-break">{successEmail}</strong> is ready. Sign in to continue.</p>
        <Link to="/login" state={{ registeredEmail: successEmail }} className="btn btn-primary btn-auth-submit w-100">Continue to sign in</Link>
        <p className="auth-form-switch mt-4 mb-0">No automatic sign-in is performed after registration.</p>
      </section>
    )
  }

  return (
    <div className="auth-form-card auth-registration">
      <span className="auth-form-eyebrow">GET STARTED</span>
      <h1 className="auth-form-title">Create your account</h1>
      <p className="auth-form-intro">Register as a student to begin requesting university documents.</p>
      {message && <div className="mb-4"><FeedbackBanner variant="error" message={message.text} requestId={message.requestId} onDismiss={() => setMessage(null)} /></div>}
      <form onSubmit={(event) => void handleSubmit(event)} noValidate>
        <div className="form-field">
          <label htmlFor="register-name" className="form-label">Full name</label>
          <input id="register-name" name="fullName" className={`form-control${errors.fullName ? ' is-invalid' : ''}`}
            type="text" autoComplete="name" placeholder="Your full name" maxLength={150} value={form.fullName}
            onChange={(event) => updateField('fullName', event.target.value)} aria-invalid={Boolean(errors.fullName)}
            aria-describedby={errors.fullName ? 'register-name-error' : undefined} disabled={submitting} />
          {errors.fullName && <div className="invalid-feedback" id="register-name-error">{errors.fullName}</div>}
        </div>
        <div className="form-field">
          <label htmlFor="register-email" className="form-label">Email address</label>
          <input id="register-email" name="email" className={`form-control${errors.email ? ' is-invalid' : ''}`}
            type="email" autoComplete="email" inputMode="email" placeholder="you@example.com" value={form.email}
            onChange={(event) => updateField('email', event.target.value)} aria-invalid={Boolean(errors.email)}
            aria-describedby={errors.email ? 'register-email-error' : undefined} disabled={submitting} />
          {errors.email && <div className="invalid-feedback" id="register-email-error">{errors.email}</div>}
        </div>
        <div className="form-field">
          <label htmlFor="register-password" className="form-label">Password</label>
          <input id="register-password" name="password" className={`form-control${errors.password ? ' is-invalid' : ''}`}
            type="password" autoComplete="new-password" placeholder="Create a password" value={form.password}
            onChange={(event) => updateField('password', event.target.value)} aria-invalid={Boolean(errors.password)}
            aria-describedby={errors.password ? 'register-password-error register-password-help' : 'register-password-help'} disabled={submitting} />
          {errors.password && <div className="invalid-feedback" id="register-password-error">{errors.password}</div>}
          <div id="register-password-help" className="form-text">8–72 characters with uppercase, lowercase, a number and a special character.</div>
        </div>
        <div className="form-field">
          <label htmlFor="register-confirm" className="form-label">Confirm password</label>
          <input id="register-confirm" name="confirmPassword" className={`form-control${errors.confirmPassword ? ' is-invalid' : ''}`}
            type="password" autoComplete="new-password" placeholder="Repeat your password" value={form.confirmPassword}
            onChange={(event) => updateField('confirmPassword', event.target.value)} aria-invalid={Boolean(errors.confirmPassword)}
            aria-describedby={errors.confirmPassword ? 'register-confirm-error' : undefined} disabled={submitting} />
          {errors.confirmPassword && <div className="invalid-feedback" id="register-confirm-error">{errors.confirmPassword}</div>}
        </div>
        <button className="btn btn-primary btn-auth-submit w-100" type="submit" disabled={submitting}>
          {submitting ? <><span className="spinner-border spinner-border-sm me-2" aria-hidden="true" />Creating account...</> : 'Create student account'}
        </button>
      </form>
      <div className="auth-form-divider" />
      <p className="auth-form-switch">Already have an account? <Link to="/login">Sign in</Link></p>
    </div>
  )
}
