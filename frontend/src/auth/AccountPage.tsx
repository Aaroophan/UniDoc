import { useNavigate } from 'react-router-dom'
import { ACCOUNT_STATUS_LABELS, ROLE_LABELS } from '../api/contracts'
import { useAuth } from './AuthContext'
import { PageHeader } from '../components/PageHeader'

export function AccountPage() {
  const { user, logout } = useAuth()
  const navigate = useNavigate()
  if (!user) return null

  const initials = user.fullName.trim().split(/\s+/).slice(0, 2).map((part) => part[0].toUpperCase()).join('')

  function signOut() {
    logout()
    navigate('/login', { replace: true, state: null })
  }

  return (
    <>
      <PageHeader eyebrow="Personal information" title="My account" description="Your UniDoc identity and access details." />
      <div className="account-grid">
        <section className="surface-card account-information" aria-labelledby="account-details-heading">
          <div className="account-profile-row">
            <div className="account-big-avatar" aria-hidden="true">{initials}</div>
            <div>
              <h2 className="h4 mb-1" id="account-details-heading">{user.fullName}</h2>
              <p className="text-muted mb-0 text-break">{user.email}</p>
            </div>
          </div>
          <dl className="account-detail-list">
            <div><dt>Full name</dt><dd>{user.fullName}</dd></div>
            <div><dt>Email address</dt><dd>{user.email}</dd></div>
            <div><dt>Account role</dt><dd>{ROLE_LABELS[user.role]}</dd></div>
            <div><dt>Account status</dt><dd><span className="account-status-active">{ACCOUNT_STATUS_LABELS[user.status]}</span></dd></div>
          </dl>
        </section>
        <aside className="account-aside">
          <div className="surface-card account-security-card">
            <p className="section-eyebrow mb-3">Session and security</p>
            <h2 className="h5">You're signed in securely.</h2>
            <p>Your session is active in this browser tab. Signing out clears the current tab's session.</p>
            <button type="button" className="btn btn-outline-primary w-100" onClick={signOut}>Sign out of UniDoc</button>
          </div>
          <div className="account-info-note"><strong>About your details</strong><p className="mb-0">Account information is managed securely by UniDoc. Student academic profile details are kept separately.</p></div>
        </aside>
      </div>
    </>
  )
}
