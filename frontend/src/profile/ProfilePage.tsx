import { useEffect, useRef, useState } from 'react'
import type { FormEvent } from 'react'
import { Link } from 'react-router-dom'
import type { FieldErrors } from '../api/contracts'
import { getApiError, getSafeErrorMessage } from '../api/errors'
import { useAuth } from '../auth/AuthContext'
import { FeedbackBanner } from '../components/FeedbackBanner'
import type { FeedbackVariant } from '../components/FeedbackBanner'
import { PageHeader } from '../components/PageHeader'
import { PageState } from '../components/PageState'
import { getStudentProfile, hasCompleteStudentProfile, updateStudentProfile } from './profileApi'
import type { StudentProfileResponse } from './profileApi'
import { PROFILE_FIELDS, formValuesFromProfile, toUpdateRequest, validateProfile } from './profileValidation'
import type { ProfileField, ProfileFormValues } from './profileValidation'

interface Feedback {
  variant: FeedbackVariant
  message: string
  requestId?: string | null
}

interface FieldConfig {
  key: ProfileField
  label: string
  required: boolean
  maxLength?: number
  help?: string
  type?: 'text' | 'tel' | 'number'
  autoComplete?: string
}

const FIELD_CONFIG: readonly FieldConfig[] = [
  { key: 'fullName', label: 'Full name', required: true, maxLength: 150, autoComplete: 'name' },
  { key: 'registrationNumber', label: 'Registration number', required: true, maxLength: 50, help: 'Letters and numbers, with slash (/) or hyphen (-). Your registration number must be unique.' },
  { key: 'phone', label: 'Phone number', required: false, maxLength: 30, type: 'tel', autoComplete: 'tel', help: 'Optional. Use 7–30 characters: numbers, spaces, +, - and parentheses.' },
  { key: 'faculty', label: 'Faculty', required: true, maxLength: 100, autoComplete: 'organization' },
  { key: 'department', label: 'Department', required: true, maxLength: 100 },
  { key: 'degreeProgramme', label: 'Degree programme', required: true, maxLength: 150 },
  { key: 'academicYear', label: 'Academic year', required: true, type: 'number', help: 'Enter your current academic year, from 1 to 8.' },
]

function FormField({ config, value, error, onChange, inputRef }: {
  config: FieldConfig
  value: string
  error?: string
  onChange: (value: string) => void
  inputRef: (element: HTMLInputElement | null) => void
}) {
  const id = `profile-${config.key}`
  const describedBy = [config.help ? `${id}-help` : null, error ? `${id}-error` : null].filter(Boolean).join(' ') || undefined
  return (
    <div className="form-field">
      <label className="form-label" htmlFor={id}>{config.label}{!config.required && <span className="profile-optional"> (optional)</span>}</label>
      <input
        ref={inputRef}
        id={id}
        name={config.key}
        className={`form-control${error ? ' is-invalid' : ''}`}
        type={config.type ?? 'text'}
        inputMode={config.type === 'number' ? 'numeric' : undefined}
        min={config.type === 'number' ? 1 : undefined}
        max={config.type === 'number' ? 8 : undefined}
        step={config.type === 'number' ? 1 : undefined}
        maxLength={config.maxLength}
        required={config.required}
        autoComplete={config.autoComplete ?? 'off'}
        aria-invalid={Boolean(error)}
        aria-describedby={describedBy}
        value={value}
        onChange={(event) => onChange(event.target.value)}
      />
      {config.help && <div className="form-text" id={`${id}-help`}>{config.help}</div>}
      {error && <div className="invalid-feedback" id={`${id}-error`}>{error}</div>}
    </div>
  )
}

function ProfileSummary({ profile }: { profile: StudentProfileResponse }) {
  const rows = [
    ['Full name', profile.fullName],
    ['Email address', profile.email],
    ['Registration number', profile.registrationNumber],
    ['Phone', profile.phone || 'Not provided'],
    ['Faculty', profile.faculty],
    ['Department', profile.department],
    ['Degree programme', profile.degreeProgramme],
    ['Academic year', profile.academicYear == null ? null : `Year ${profile.academicYear}`],
  ]
  return (
    <dl className="profile-summary-list">
      {rows.map(([label, value]) => (
        <div key={label}>
          <dt>{label}</dt>
          <dd>{value || 'Not provided'}</dd>
        </div>
      ))}
    </dl>
  )
}

export function ProfilePage() {
  const { refreshCurrentUser } = useAuth()
  const [loadAttempt, setLoadAttempt] = useState(0)
  const [status, setStatus] = useState<'loading' | 'error' | 'ready'>('loading')
  const [loadError, setLoadError] = useState<Feedback | null>(null)
  const [profile, setProfile] = useState<StudentProfileResponse | null>(null)
  const [form, setForm] = useState<ProfileFormValues | null>(null)
  const [editing, setEditing] = useState(false)
  const [submitting, setSubmitting] = useState(false)
  const [errors, setErrors] = useState<FieldErrors>({})
  const [feedback, setFeedback] = useState<Feedback | null>(null)
  const fieldRefs = useRef<Partial<Record<ProfileField, HTMLInputElement | null>>>({})

  useEffect(() => {
    const controller = new AbortController()
    async function load() {
      try {
        const result = await getStudentProfile(controller.signal)
        if (controller.signal.aborted) return
        setProfile(result)
        setForm(formValuesFromProfile(result))
        setEditing(!hasCompleteStudentProfile(result))
        setErrors({})
        setLoadError(null)
        setStatus('ready')
      } catch (error: unknown) {
        if (controller.signal.aborted) return
        setLoadError({ variant: 'error', message: getSafeErrorMessage(error, 'Unable to load your profile.'), requestId: getApiError(error)?.requestId })
        setStatus('error')
      }
    }
    void load()
    return () => controller.abort()
  }, [loadAttempt])

  function focusFirstInvalid(fields: FieldErrors) {
    for (const field of PROFILE_FIELDS) {
      if (fields[field]) {
        fieldRefs.current[field]?.focus()
        return
      }
    }
  }

  function changeField(field: ProfileField, value: string) {
    setForm((previous) => previous ? { ...previous, [field]: value } : previous)
    setErrors((previous) => previous[field] ? { ...previous, [field]: '' } : previous)
    setFeedback(null)
  }

  async function save(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (submitting || !form) return
    setFeedback(null)
    const validationErrors = validateProfile(form)
    setErrors(validationErrors)
    if (Object.keys(validationErrors).length) {
      focusFirstInvalid(validationErrors)
      return
    }

    setSubmitting(true)
    try {
      const updated = await updateStudentProfile(toUpdateRequest(form))
      setProfile(updated)
      setForm(formValuesFromProfile(updated))
      setEditing(!hasCompleteStudentProfile(updated))
      setErrors({})
      setFeedback({ variant: 'success', message: 'Your academic profile has been saved.' })
      // Revalidate the public account name for navigation, Account and sessionStorage.
      // A failure here must not erase a successfully saved profile.
      try {
        await refreshCurrentUser()
      } catch {
        setFeedback({ variant: 'warning', message: 'Your profile was saved, but your account display could not be refreshed. Reload when the connection is available.' })
      }
    } catch (error: unknown) {
      const apiError = getApiError(error)
      const fieldErrors = { ...(apiError?.fieldErrors ?? {}) }
      if (apiError?.code === 'REGISTRATION_NUMBER_ALREADY_EXISTS') {
        fieldErrors.registrationNumber = 'This registration number is already in use. Check it and try again.'
      }
      setErrors(fieldErrors)
      if (Object.keys(fieldErrors).length) focusFirstInvalid(fieldErrors)
      const message = apiError?.code === 'REGISTRATION_NUMBER_ALREADY_EXISTS'
        ? 'Please correct your registration number before saving.'
        : getSafeErrorMessage(error, 'Your profile could not be saved.')
      setFeedback({
        variant: apiError === null ? 'warning' : 'error',
        message: apiError === null ? 'Could not confirm whether the profile was saved. Reload it when the connection returns before trying again.' : message,
        requestId: apiError?.requestId,
      })
    } finally {
      setSubmitting(false)
    }
  }

  const complete = profile ? hasCompleteStudentProfile(profile) : false
  return (
    <>
      <PageHeader
        eyebrow="Student information"
        title="My academic profile"
        description="Keep your identity and academic details accurate before requesting university documents."
      />

      {status === 'loading' && <PageState status="loading" message="Loading your student profile..." />}
      {status === 'error' && <PageState status="error" message={loadError?.message ?? 'Unable to load your profile.'} requestId={loadError?.requestId} onRetry={() => { setStatus('loading'); setLoadAttempt((value) => value + 1) }} />}
      {status === 'ready' && profile && form && (
        <div className="profile-layout">
          <section className="surface-card profile-main" aria-labelledby="profile-details-heading">
            <div className="profile-card-heading">
              <div>
                <p className="section-eyebrow mb-2">{complete ? 'Profile ready' : 'Action required'}</p>
                <h2 className="h4 mb-2" id="profile-details-heading">{complete ? 'Your academic details' : 'Complete your profile'}</h2>
                <p className="mb-0 text-muted">
                  {complete ? 'You can review and update this information at any time.' : 'Your profile is incomplete. Fill in the required details to become eligible to submit document requests.'}
                </p>
              </div>
              {complete && !editing && <button type="button" className="btn btn-outline-primary" onClick={() => { setEditing(true); setFeedback(null) }}>Edit profile</button>}
            </div>

            {feedback && <div className="profile-feedback"><FeedbackBanner {...feedback} onDismiss={() => setFeedback(null)} /></div>}

            {editing ? (
              <form onSubmit={(event) => void save(event)} noValidate className="profile-form">
                <div className="profile-email">
                  <label className="form-label" htmlFor="profile-email">Email address</label>
                  <input className="form-control" id="profile-email" type="email" value={profile.email} readOnly aria-describedby="profile-email-help" />
                  <div className="form-text" id="profile-email-help">Your account email cannot be changed here.</div>
                </div>
                <div className="profile-form-grid">
                  {FIELD_CONFIG.map((config) => (
                    <FormField
                      key={config.key}
                      config={config}
                      value={form[config.key]}
                      error={errors[config.key]}
                      onChange={(value) => changeField(config.key, value)}
                      inputRef={(element) => { fieldRefs.current[config.key] = element }}
                    />
                  ))}
                </div>
                <div className="profile-form-actions">
                  <button type="submit" className="btn btn-primary" disabled={submitting} aria-busy={submitting}>
                    {submitting ? <><span className="spinner-border spinner-border-sm me-2" aria-hidden="true" />Saving profile...</> : complete ? 'Save changes' : 'Complete profile'}
                  </button>
                  {complete && <button type="button" className="btn btn-outline-secondary" disabled={submitting} onClick={() => { setForm(formValuesFromProfile(profile)); setErrors({}); setEditing(false); setFeedback(null) }}>Cancel</button>}
                </div>
              </form>
            ) : (
              <div className="profile-view">
                <ProfileSummary profile={profile} />
                {profile.updatedAt && <p className="profile-updated mb-0">Last updated {new Date(profile.updatedAt).toLocaleString()}</p>}
              </div>
            )}
          </section>

          <aside className="profile-side" aria-label="Profile guidance">
            <section className="surface-card profile-guide">
              <p className="section-eyebrow mb-2">What happens next</p>
              <h2 className="h5">{complete ? 'You are ready for requests' : 'One step before requesting'}</h2>
              <p>{complete ? 'Your academic profile is complete. You can continue to the request workspace when you need a document.' : 'UniDoc needs your registration and programme information before it can accept a document request.'}</p>
              {complete ? (
                <Link to="/requests/new" className="btn btn-outline-primary w-100">Continue to new request</Link>
              ) : (
                <p className="profile-next-step mb-0">Finish the required fields on this page and choose <strong>Complete profile</strong>.</p>
              )}
            </section>
            <div className="profile-privacy-note">
              <strong>About your information</strong>
              <p className="mb-0">Only your own academic profile can be edited here. Your email and account role are managed separately.</p>
            </div>
          </aside>
        </div>
      )}
    </>
  )
}
