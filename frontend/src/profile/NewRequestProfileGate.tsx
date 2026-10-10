import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { getApiError, getSafeErrorMessage } from '../api/errors'
import { PageHeader } from '../components/PageHeader'
import { PageState } from '../components/PageState'
import { PlannedFeaturePage } from '../pages/PlannedFeaturePage'
import { getStudentProfile, hasCompleteStudentProfile } from './profileApi'

type GateState = 'loading' | 'error' | 'incomplete' | 'complete'

/** Issue 6 usability gate only. Issue 11 must also enforce the prerequisite on POST. */
export function NewRequestProfileGate() {
  const [state, setState] = useState<GateState>('loading')
  const [error, setError] = useState<{ message: string; requestId?: string | null } | null>(null)
  const [attempt, setAttempt] = useState(0)

  useEffect(() => {
    const controller = new AbortController()
    async function load() {
      try {
        const profile = await getStudentProfile(controller.signal)
        if (!controller.signal.aborted) {
          setState(hasCompleteStudentProfile(profile) ? 'complete' : 'incomplete')
          setError(null)
        }
      } catch (failure: unknown) {
        if (!controller.signal.aborted) {
          setError({ message: getSafeErrorMessage(failure, 'Unable to check your profile.'), requestId: getApiError(failure)?.requestId })
          setState('error')
        }
      }
    }
    void load()
    return () => controller.abort()
  }, [attempt])

  if (state === 'complete') {
    return <PlannedFeaturePage eyebrow="Student workspace" title="New document request" description="Request a document online and select electronic or physical delivery." issue={12} />
  }
  return (
    <>
      <PageHeader eyebrow="Student workspace" title="New document request" description="Your academic profile must be complete before you can submit a document request." />
      {state === 'loading' && <PageState status="loading" message="Checking your academic profile..." />}
      {state === 'error' && <PageState status="error" title="Could not verify your profile" message={error?.message ?? 'Unable to check your profile.'} requestId={error?.requestId} onRetry={() => { setState('loading'); setAttempt((value) => value + 1) }} />}
      {state === 'incomplete' && (
        <section className="surface-card profile-required" aria-labelledby="profile-required-title">
          <p className="section-eyebrow mb-2">Action required</p>
          <h2 className="h4 mb-3" id="profile-required-title">Complete your academic profile first</h2>
          <p className="text-muted">Add your registration number, faculty, department, degree programme and academic year. UniDoc checks these saved details before accepting requests.</p>
          <Link to="/profile" className="btn btn-primary">Complete my profile</Link>
        </section>
      )}
    </>
  )
}
