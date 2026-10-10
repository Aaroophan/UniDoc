import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { getApiError, getSafeErrorMessage } from '../api/errors'
import { FeedbackBanner } from '../components/FeedbackBanner'
import { PageHeader } from '../components/PageHeader'
import { getStudentProfile, hasCompleteStudentProfile } from './profileApi'

type ProfileReadiness = 'checking' | 'complete' | 'incomplete' | 'error'

/** Issue 6 guidance on the student's initial workspace; tracking belongs to Issue 12. */
export function StudentRequestsLanding() {
  const [readiness, setReadiness] = useState<ProfileReadiness>('checking')
  const [feedback, setFeedback] = useState<{ message: string; requestId?: string | null } | null>(null)
  const [attempt, setAttempt] = useState(0)

  useEffect(() => {
    const controller = new AbortController()
    async function load() {
      try {
        const profile = await getStudentProfile(controller.signal)
        if (!controller.signal.aborted) {
          setReadiness(hasCompleteStudentProfile(profile) ? 'complete' : 'incomplete')
          setFeedback(null)
        }
      } catch (error: unknown) {
        if (!controller.signal.aborted) {
          setReadiness('error')
          setFeedback({ message: getSafeErrorMessage(error, 'Unable to verify your profile.'), requestId: getApiError(error)?.requestId })
        }
      }
    }
    void load()
    return () => controller.abort()
  }, [attempt])

  return (
    <>
      <PageHeader eyebrow="Student workspace" title="My requests" description="Track your document requests, their progress and next actions." />
      {readiness === 'checking' && <p className="profile-readiness-note" role="status">Checking your academic profile...</p>}
      {readiness === 'incomplete' && (
        <section className="profile-readiness-alert" aria-labelledby="readiness-title">
          <div>
            <p className="section-eyebrow mb-2">Action required</p>
            <h2 className="h5 mb-2" id="readiness-title">Complete your academic profile</h2>
            <p className="mb-0">Add your registration and study details now so you are ready to submit a document request when the request feature becomes available.</p>
          </div>
          <Link to="/profile" className="btn btn-primary">Complete my profile</Link>
        </section>
      )}
      {readiness === 'error' && feedback && (
        <div className="mb-4">
          <FeedbackBanner variant="warning" message={feedback.message} requestId={feedback.requestId} />
          <button type="button" className="btn btn-outline-primary mt-2" onClick={() => { setReadiness('checking'); setAttempt((value) => value + 1) }}>Retry profile check</button>
        </div>
      )}
      <section className="planned-card" aria-labelledby="requests-planned-title">
        <div className="planned-decoration" aria-hidden="true"><span>UD</span></div>
        <div className="planned-copy">
          <p className="section-eyebrow mb-2">Workspace ready</p>
          <h2 id="requests-planned-title">Request tracking is coming next.</h2>
          <p>This area will let you submit and track document requests when Issues 11 and 12 are implemented. Your account and academic profile can be prepared today.</p>
          <div className="planned-actions">
            <Link to="/profile" className="btn btn-outline-primary">My academic profile</Link>
            {readiness === 'complete' && <Link to="/requests/new" className="btn btn-primary">View new request page</Link>}
          </div>
        </div>
      </section>
    </>
  )
}
