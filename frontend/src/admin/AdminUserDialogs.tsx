import { useRef, useState } from 'react'
import type { FormEvent } from 'react'
import type { FieldErrors, UserSummary } from '../api/contracts'
import { getApiError, getSafeErrorMessage } from '../api/errors'
import { normalizeEmail } from '../auth/validation'
import { ConfirmDialog } from '../components/ConfirmDialog'
import { FeedbackBanner } from '../components/FeedbackBanner'
import { focusFirstInvalidField } from '../components/formFocus'
import { changeManagedAccountStatus, createManagedAccount, resetManagedAccountPassword } from './adminUsersApi'
import { validateManagedAccount, validatePasswordReset } from './adminUsersValidation'
import type { CreateAccountForm, PasswordResetForm } from './adminUsersValidation'

type Alert = { message: string; requestId?: string | null; warning?: boolean }

function focusInvalid(form: HTMLFormElement | null, errors: FieldErrors, fields: string[]) {
  const first = fields.find((field) => errors[field])
  if (first) focusFirstInvalidField(form, [first])
}

interface InputFieldProps {
  name: string
  label: string
  value: string
  onChange: (value: string) => void
  error?: string
  type?: 'text' | 'email' | 'password'
  help?: string
  maxLength?: number
  autoComplete?: string
  disabled?: boolean
}

function InputField({ name, label, value, onChange, error, type = 'text', help, maxLength, autoComplete, disabled }: InputFieldProps) {
  const id = `admin-user-${name}`
  return (
    <div className="form-field">
      <label htmlFor={id} className="form-label">{label}</label>
      <input id={id} name={name} type={type} value={value} maxLength={maxLength}
        autoComplete={autoComplete} disabled={disabled}
        className={`form-control${error ? ' is-invalid' : ''}`}
        aria-invalid={Boolean(error)}
        aria-describedby={[error ? `${id}-error` : null, help ? `${id}-help` : null].filter(Boolean).join(' ') || undefined}
        onChange={(event) => onChange(event.target.value)} />
      {error && <div className="invalid-feedback" id={`${id}-error`}>{error}</div>}
      {help && <div className="form-text" id={`${id}-help`}>{help}</div>}
    </div>
  )
}

const initialCreate: CreateAccountForm = {
  fullName: '', email: '', role: 'STAFF', password: '', confirmPassword: '',
}

export function CreateAccountDialog({ onClose, onCreated, onUncertain }: {
  onClose: () => void
  onCreated: (user: UserSummary) => void
  onUncertain: (email: string) => void
}) {
  const formRef = useRef<HTMLFormElement>(null)
  const [form, setForm] = useState<CreateAccountForm>(initialCreate)
  const [errors, setErrors] = useState<FieldErrors>({})
  const [alert, setAlert] = useState<Alert | null>(null)
  const [busy, setBusy] = useState(false)
  const [uncertain, setUncertain] = useState(false)

  function change<K extends keyof CreateAccountForm>(field: K, value: CreateAccountForm[K]) {
    setForm((current) => ({ ...current, [field]: value }))
    setErrors((current) => ({ ...current, [field]: '' }))
    setAlert(null)
  }

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (busy || uncertain) return
    setAlert(null)
    const next = validateManagedAccount(form)
    setErrors(next)
    if (Object.keys(next).length) {
      focusInvalid(formRef.current, next, ['fullName', 'email', 'role', 'password', 'confirmPassword'])
      return
    }
    setBusy(true)
    try {
      const created = await createManagedAccount({
        fullName: form.fullName.trim(),
        email: normalizeEmail(form.email),
        role: form.role,
        password: form.password,
      })
      onCreated(created)
    } catch (error: unknown) {
      const response = getApiError(error)
      const nextErrors = { ...response?.fieldErrors }
      if (response?.code === 'EMAIL_ALREADY_EXISTS') nextErrors.email = 'An account already uses this email address.'
      setErrors(nextErrors)
      focusInvalid(formRef.current, nextErrors, ['fullName', 'email', 'role', 'password', 'confirmPassword'])
      if (!response) {
        setUncertain(true)
        onUncertain(form.email)
        setAlert({ warning: true, message: 'The result could not be confirmed. The account list is refreshing. Close this dialog and check whether the account was created before trying again.' })
      } else {
        setAlert({ message: getSafeErrorMessage(error, 'Unable to create the account.'), requestId: response.requestId })
      }
    } finally {
      setBusy(false)
    }
  }

  return (
    <ConfirmDialog open title="Create a managed account"
      description="Create an active staff or administrator account. Students register separately."
      confirmLabel="Create account" busy={busy} onCancel={onClose}
      onConfirm={() => formRef.current?.requestSubmit()}>
      <form ref={formRef} onSubmit={(event) => void submit(event)} noValidate className="admin-modal-form">
        {alert && <div className="mb-3"><FeedbackBanner variant={alert.warning ? 'warning' : 'error'} message={alert.message} requestId={alert.requestId} /></div>}
        <InputField name="fullName" label="Full name" value={form.fullName} maxLength={150} autoComplete="off" disabled={busy || uncertain}
          error={errors.fullName} onChange={(value) => change('fullName', value)} />
        <InputField name="email" label="Email address" value={form.email} type="email" maxLength={254} autoComplete="off" disabled={busy || uncertain}
          error={errors.email} onChange={(value) => change('email', value)} />
        <div className="form-field">
          <label className="form-label" htmlFor="admin-user-role">Account role</label>
          <select id="admin-user-role" name="role" className={`form-select${errors.role ? ' is-invalid' : ''}`}
            aria-invalid={Boolean(errors.role)} aria-describedby={errors.role ? 'admin-role-error' : undefined}
            value={form.role} disabled={busy || uncertain}
            onChange={(event) => change('role', event.target.value as 'STAFF' | 'ADMIN')}>
            <option value="STAFF">Staff</option>
            <option value="ADMIN">Administrator</option>
          </select>
          {errors.role && <div className="invalid-feedback" id="admin-role-error">{errors.role}</div>}
        </div>
        <InputField name="password" label="Initial password" type="password" autoComplete="new-password"
          value={form.password} disabled={busy || uncertain} error={errors.password}
          help="8–72 characters, including uppercase, lowercase, a number and a special character."
          onChange={(value) => change('password', value)} />
        <InputField name="confirmPassword" label="Confirm initial password" type="password" autoComplete="new-password"
          value={form.confirmPassword} disabled={busy || uncertain} error={errors.confirmPassword}
          onChange={(value) => change('confirmPassword', value)} />
        <p className="admin-credentials-note mb-0">Communicate the initial password securely outside UniDoc. It is not emailed, and no forced password-change flow is enabled.</p>
      </form>
    </ConfirmDialog>
  )
}

export function StatusDialog({ user, onClose, onChanged, onUncertain }: {
  user: UserSummary
  onClose: () => void
  onChanged: (user: UserSummary) => void
  onUncertain: (email: string) => void
}) {
  const next = user.status === 'ACTIVE' ? 'INACTIVE' : 'ACTIVE'
  const [busy, setBusy] = useState(false)
  const [alert, setAlert] = useState<Alert | null>(null)
  const [uncertain, setUncertain] = useState(false)

  async function confirm() {
    if (busy || uncertain) return
    setBusy(true)
    try {
      onChanged(await changeManagedAccountStatus(user.id, next))
    } catch (error: unknown) {
      const response = getApiError(error)
      if (!response) {
        setUncertain(true)
        onUncertain(user.email)
        setAlert({ warning: true, message: 'Could not confirm the status change. The list is refreshing. Close this dialog and check the current status before trying again.' })
      } else {
        setAlert({
          message: response.code === 'SELF_DEACTIVATION_NOT_ALLOWED'
            ? 'You cannot deactivate your own administrator account.'
            : getSafeErrorMessage(error, 'Unable to change account status.'),
          requestId: response.requestId,
        })
      }
    } finally {
      setBusy(false)
    }
  }

  return (
    <ConfirmDialog open title={`${next === 'INACTIVE' ? 'Deactivate' : 'Activate'} account`}
      description={`${next === 'INACTIVE' ? 'Deactivate' : 'Activate'} ${user.fullName} (${user.email})?`}
      confirmLabel={next === 'INACTIVE' ? 'Deactivate account' : 'Activate account'}
      destructive={next === 'INACTIVE'} busy={busy} onCancel={onClose} onConfirm={() => void confirm()}>
      {next === 'INACTIVE' && <p className="admin-credentials-note">Deactivation prevents sign-in and revokes every existing session on its next authenticated request.</p>}
      {next === 'ACTIVE' && <p className="admin-credentials-note">An active account can sign in again with its existing password. Previously revoked tokens stay invalid.</p>}
      {alert && <FeedbackBanner variant={alert.warning ? 'warning' : 'error'} message={alert.message} requestId={alert.requestId} />}
    </ConfirmDialog>
  )
}

export function PasswordResetDialog({ user, onClose, onReset, onUncertain }: {
  user: UserSummary
  onClose: () => void
  onReset: () => void
  onUncertain: (email: string) => void
}) {
  const formRef = useRef<HTMLFormElement>(null)
  const [form, setForm] = useState<PasswordResetForm>({ newPassword: '', confirmPassword: '' })
  const [errors, setErrors] = useState<FieldErrors>({})
  const [alert, setAlert] = useState<Alert | null>(null)
  const [busy, setBusy] = useState(false)
  const [uncertain, setUncertain] = useState(false)

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (busy || uncertain) return
    setAlert(null)
    const nextErrors = validatePasswordReset(form)
    setErrors(nextErrors)
    if (Object.keys(nextErrors).length) {
      focusInvalid(formRef.current, nextErrors, ['newPassword', 'confirmPassword'])
      return
    }
    setBusy(true)
    try {
      await resetManagedAccountPassword(user.id, form)
      setForm({ newPassword: '', confirmPassword: '' })
      onReset()
    } catch (error: unknown) {
      const response = getApiError(error)
      if (!response) {
        setUncertain(true)
        onUncertain(user.email)
        setAlert({ warning: true, message: 'Password reset could not be confirmed. The account list is refreshing; verify the recipient can sign in before attempting another reset.' })
      } else {
        setErrors(response.fieldErrors ?? {})
        focusInvalid(formRef.current, response.fieldErrors ?? {}, ['newPassword', 'confirmPassword'])
        setAlert({ message: getSafeErrorMessage(error, 'Unable to reset the password.'), requestId: response.requestId })
      }
    } finally {
      setBusy(false)
    }
  }

  return (
    <ConfirmDialog open title="Reset account password"
      description={`Set a new password for ${user.fullName} (${user.email}). The existing password cannot be viewed.`}
      confirmLabel="Reset password" busy={busy} onCancel={onClose}
      onConfirm={() => formRef.current?.requestSubmit()}>
      <form ref={formRef} onSubmit={(event) => void submit(event)} noValidate className="admin-modal-form">
        {alert && <div className="mb-3"><FeedbackBanner variant={alert.warning ? 'warning' : 'error'} message={alert.message} requestId={alert.requestId} /></div>}
        <InputField name="newPassword" label="New password" type="password" autoComplete="new-password"
          value={form.newPassword} disabled={busy || uncertain} error={errors.newPassword}
          help="8–72 characters with uppercase, lowercase, a number and a special character."
          onChange={(value) => { setForm((old) => ({ ...old, newPassword: value })); setErrors((old) => ({ ...old, newPassword: '' })) }} />
        <InputField name="confirmPassword" label="Confirm new password" type="password" autoComplete="new-password"
          value={form.confirmPassword} disabled={busy || uncertain} error={errors.confirmPassword}
          onChange={(value) => { setForm((old) => ({ ...old, confirmPassword: value })); setErrors((old) => ({ ...old, confirmPassword: '' })) }} />
        <p className="admin-credentials-note mb-0">The new password must be communicated securely outside UniDoc. All existing sessions for this account will be revoked.</p>
      </form>
    </ConfirmDialog>
  )
}
