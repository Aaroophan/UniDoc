import { useEffect, useRef, useState } from 'react'
import type { FormEvent } from 'react'
import { useNavigate } from 'react-router-dom'
import type { PageResponse, UserRole, UserStatus, UserSummary } from '../api/contracts'
import { ACCOUNT_STATUS_LABELS, ROLE_LABELS } from '../api/contracts'
import { getApiError, getSafeErrorMessage } from '../api/errors'
import { useAuth } from '../auth/AuthContext'
import { FeedbackBanner } from '../components/FeedbackBanner'
import type { FeedbackVariant } from '../components/FeedbackBanner'
import { PageHeader } from '../components/PageHeader'
import { PageState } from '../components/PageState'
import { Pagination } from '../components/Pagination'
import { CreateAccountDialog, PasswordResetDialog, StatusDialog } from './AdminUserDialogs'
import { listAdminUsers } from './adminUsersApi'
import type { UserFilters } from './adminUsersApi'

type SortColumn = NonNullable<UserFilters['sort']>
type SortDirection = NonNullable<UserFilters['direction']>
type Filters = { q: string; role: UserRole | ''; status: UserStatus | ''; sort: SortColumn; direction: SortDirection }
type LoadState = 'loading' | 'error' | 'ready' | 'refreshing' | 'stale'
type Message = { variant: FeedbackVariant; text: string; requestId?: string | null }
type Dialog = { action: 'status' | 'reset'; user: UserSummary } | null

const DEFAULT_FILTERS: Filters = { q: '', role: '', status: '', sort: 'createdAt', direction: 'desc' }

const dateFormatter = new Intl.DateTimeFormat(undefined, { year: 'numeric', month: 'short', day: 'numeric' })
function displayDate(isoDate: string): string {
  const date = new Date(isoDate)
  return Number.isNaN(date.getTime()) ? '—' : dateFormatter.format(date)
}

function AccountStatus({ status }: { status: UserStatus }) {
  return <span className={`admin-account-status ${status === 'ACTIVE' ? 'is-active' : 'is-inactive'}`}>{ACCOUNT_STATUS_LABELS[status]}</span>
}

function UserActions({ account, currentUserId, onStatus, onReset }: {
  account: UserSummary
  currentUserId: number
  onStatus: (user: UserSummary) => void
  onReset: (user: UserSummary) => void
}) {
  const ownActiveAccount = account.id === currentUserId && account.status === 'ACTIVE'
  return (
    <div className="admin-user-actions">
      {ownActiveAccount ? (
        <span className="small text-muted" title="You cannot deactivate your own administrator account.">Own account</span>
      ) : (
        <button type="button" className="btn btn-outline-secondary btn-sm" onClick={() => onStatus(account)}>
          {account.status === 'ACTIVE' ? 'Deactivate' : 'Activate'}
        </button>
      )}
      <button type="button" className="btn btn-outline-primary btn-sm" onClick={() => onReset(account)}>Reset password</button>
    </div>
  )
}

export function AdminUsersPage() {
  const { user, logout } = useAuth()
  const navigate = useNavigate()
  const [draftQ, setDraftQ] = useState('')
  const [filters, setFilters] = useState<Filters>(DEFAULT_FILTERS)
  const [page, setPage] = useState(0)
  const [reload, setReload] = useState(0)
  const [loadState, setLoadState] = useState<LoadState>('loading')
  const [results, setResults] = useState<PageResponse<UserSummary> | null>(null)
  const [loadError, setLoadError] = useState<{ message: string; requestId?: string | null } | null>(null)
  const [feedback, setFeedback] = useState<Message | null>(null)
  const [createOpen, setCreateOpen] = useState(false)
  const [dialog, setDialog] = useState<Dialog>(null)
  const loadedKey = useRef<string | null>(null)
  const viewKey = JSON.stringify([filters, page])

  useEffect(() => {
    const controller = new AbortController()
    async function fetchUsers() {
      const sameResults = loadedKey.current === viewKey
      setLoadState(sameResults ? 'refreshing' : 'loading')
      setLoadError(null)
      try {
        const data = await listAdminUsers({ ...filters, page, size: 20 }, controller.signal)
        if (!controller.signal.aborted) {
          if (data.totalPages > 0 && page >= data.totalPages) {
            setPage(data.totalPages - 1)
            return
          }
          setResults(data)
          loadedKey.current = viewKey
          setLoadState('ready')
        }
      } catch (error: unknown) {
        if (!controller.signal.aborted) {
          setLoadError({ message: getSafeErrorMessage(error, 'Unable to load accounts.'), requestId: getApiError(error)?.requestId })
          setLoadState(sameResults ? 'stale' : 'error')
        }
      }
    }
    void fetchUsers()
    return () => controller.abort()
  }, [filters, page, reload, viewKey])

  function changeFilter<K extends keyof Filters>(key: K, value: Filters[K]) {
    setFilters((old) => ({ ...old, [key]: value }))
    setPage(0)
    setFeedback(null)
  }

  function applyQuery(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    changeFilter('q', draftQ.trim())
  }

  function clearFilters() {
    setDraftQ('')
    setFilters({ ...DEFAULT_FILTERS })
    setPage(0)
    setFeedback(null)
  }

  function refreshList() { setReload((n) => n + 1) }

  function uncertainResult(email: string) {
    setFeedback({ variant: 'warning', text: 'The response was interrupted. Checking the account again before any retry is important.' })
    refreshList()
    // A targeted authoritative read works even when the current filters hide this account.
    void listAdminUsers({ q: email.trim(), size: 100 }).then((found) => {
      const account = found.items.find((item) => item.email.toLowerCase() === email.trim().toLowerCase())
      setFeedback({ variant: 'warning', text: account
        ? `Account ${account.email} currently exists and is ${ACCOUNT_STATUS_LABELS[account.status].toLowerCase()}. Verify the intended change before trying again; password changes cannot be confirmed from this list.`
        : 'The account could not be confirmed from a fresh query. Check the account list and connection before attempting the action again.' })
    }).catch(() => {
      setFeedback({ variant: 'warning', text: 'Unable to recheck the account while the connection is unavailable. Do not repeat the change until the outcome is confirmed.' })
    })
  }

  if (!user) return null
  const filtered = Boolean(filters.q || filters.role || filters.status)

  return (
    <>
      <PageHeader eyebrow="Administration" title="Account management"
        description="Manage university accounts, access status and manual password resets."
        actions={<button className="btn btn-primary" type="button" onClick={() => { setFeedback(null); setCreateOpen(true) }}>Create account</button>} />

      {feedback && <div className="mb-4"><FeedbackBanner variant={feedback.variant} message={feedback.text} requestId={feedback.requestId} onDismiss={() => setFeedback(null)} /></div>}

      <section className="surface-card admin-users-filter-card" aria-labelledby="admin-users-filter-heading">
        <div className="d-flex justify-content-between align-items-center flex-wrap gap-2 mb-3">
          <h2 className="h5 mb-0" id="admin-users-filter-heading">Find accounts</h2>
          <button type="button" className="btn btn-link btn-sm" onClick={clearFilters}>Clear all filters</button>
        </div>
        <div className="admin-users-filter-grid">
          <form onSubmit={applyQuery} className="admin-user-search">
            <label className="form-label" htmlFor="admin-users-query">Name or email</label>
            <div className="input-group">
              <input id="admin-users-query" type="search" className="form-control" maxLength={100}
                value={draftQ} placeholder="Search accounts" onChange={(event) => setDraftQ(event.target.value)} />
              <button type="submit" className="btn btn-outline-primary">Search</button>
            </div>
          </form>
          <div>
            <label className="form-label" htmlFor="admin-users-role">Role</label>
            <select id="admin-users-role" className="form-select" value={filters.role}
              onChange={(event) => changeFilter('role', event.target.value as UserRole | '')}>
              <option value="">All roles</option><option value="STUDENT">Student</option><option value="STAFF">Staff</option><option value="ADMIN">Administrator</option>
            </select>
          </div>
          <div>
            <label className="form-label" htmlFor="admin-users-status">Status</label>
            <select id="admin-users-status" className="form-select" value={filters.status}
              onChange={(event) => changeFilter('status', event.target.value as UserStatus | '')}>
              <option value="">All statuses</option><option value="ACTIVE">Active</option><option value="INACTIVE">Inactive</option>
            </select>
          </div>
          <div>
            <label className="form-label" htmlFor="admin-users-sort">Sort by</label>
            <select id="admin-users-sort" className="form-select" value={filters.sort}
              onChange={(event) => changeFilter('sort', event.target.value as SortColumn)}>
              <option value="createdAt">Created date</option><option value="fullName">Name</option>
              <option value="email">Email</option><option value="role">Role</option><option value="status">Status</option>
            </select>
          </div>
          <div>
            <label className="form-label" htmlFor="admin-users-direction">Order</label>
            <select id="admin-users-direction" className="form-select" value={filters.direction}
              onChange={(event) => changeFilter('direction', event.target.value as SortDirection)}>
              <option value="desc">Descending</option><option value="asc">Ascending</option>
            </select>
          </div>
        </div>
        {filters.q && <p className="small text-muted mb-0 mt-3">Search applied: <strong className="text-break">{filters.q}</strong></p>}
      </section>

      <section className="surface-card admin-users-results" aria-labelledby="admin-users-results-heading">
        <div className="admin-users-results-heading">
          <div>
            <h2 className="h5 mb-1" id="admin-users-results-heading">Accounts</h2>
            <p className="small text-muted mb-0">{(loadState === 'ready' || loadState === 'refreshing' || loadState === 'stale') && results ? `${results.totalItems} matching account${results.totalItems === 1 ? '' : 's'}` : 'Current university accounts'}</p>
          </div>
          <button type="button" className="btn btn-outline-secondary btn-sm" disabled={loadState === 'loading' || loadState === 'refreshing'} onClick={refreshList}>Refresh</button>
        </div>
        {loadState === 'loading' && <div className="admin-users-result-content"><PageState status="loading" message="Loading accounts..." /></div>}
        {loadState === 'refreshing' && <p role="status" className="small text-muted px-4 mt-3 mb-0">Updating account list…</p>}
        {loadState === 'stale' && <div role="alert" className="px-4 mt-3"><p className="request-inline-error">Unable to refresh accounts. The previous list remains visible. {loadError?.message}</p><button className="btn btn-outline-primary btn-sm" onClick={refreshList}>Try again</button></div>}
        {loadState === 'error' && <div className="admin-users-result-content"><PageState status="error" message={loadError?.message ?? 'Unable to load accounts.'} requestId={loadError?.requestId} onRetry={refreshList} /></div>}
        {(loadState === 'ready' || loadState === 'refreshing' || loadState === 'stale') && results && results.items.length === 0 && (
          <div className="admin-users-result-content">
            <PageState status="empty" title={filtered ? 'No matching accounts' : 'No accounts available'}
              message={filtered ? 'Try clearing or changing the filters to find another account.' : 'Create a staff or administrator account to begin.'}
              action={filtered ? <button type="button" className="btn btn-outline-primary" onClick={clearFilters}>Clear filters</button> :
                <button type="button" className="btn btn-primary" onClick={() => setCreateOpen(true)}>Create account</button>} />
          </div>
        )}
        {(loadState === 'ready' || loadState === 'refreshing' || loadState === 'stale') && results && results.items.length > 0 && (
          <>
            <div className="admin-users-table-wrap d-none d-lg-block">
              <table className="table admin-users-table mb-0">
                <thead><tr><th scope="col">Account</th><th scope="col">Role</th><th scope="col">Status</th><th scope="col">Created</th><th scope="col">Actions</th></tr></thead>
                <tbody>{results.items.map((account) => (
                  <tr key={account.id}>
                    <td><strong className="d-block">{account.fullName}</strong><span className="admin-users-email">{account.email}</span></td>
                    <td>{ROLE_LABELS[account.role]}</td>
                    <td><AccountStatus status={account.status} /></td>
                    <td>{displayDate(account.createdAt)}</td>
                    <td><UserActions account={account} currentUserId={user.id}
                      onStatus={(target) => setDialog({ action: 'status', user: target })}
                      onReset={(target) => setDialog({ action: 'reset', user: target })} /></td>
                  </tr>
                ))}</tbody>
              </table>
            </div>
            <div className="admin-users-mobile d-lg-none">
              {results.items.map((account) => (
                <article key={account.id} className="admin-user-mobile-card">
                  <h3 className="h6 mb-1 text-break">{account.fullName}</h3>
                  <p className="admin-users-email mb-3">{account.email}</p>
                  <dl className="admin-user-mobile-facts">
                    <div><dt>Role</dt><dd>{ROLE_LABELS[account.role]}</dd></div>
                    <div><dt>Status</dt><dd><AccountStatus status={account.status} /></dd></div>
                    <div><dt>Created</dt><dd>{displayDate(account.createdAt)}</dd></div>
                  </dl>
                  <UserActions account={account} currentUserId={user.id}
                    onStatus={(target) => setDialog({ action: 'status', user: target })}
                    onReset={(target) => setDialog({ action: 'reset', user: target })} />
                </article>
              ))}
            </div>
            <div className="admin-users-pagination"><Pagination page={results} onPageChange={(nextPage) => setPage(nextPage)} /></div>
          </>
        )}
      </section>

      {createOpen && <CreateAccountDialog onClose={() => setCreateOpen(false)} onUncertain={uncertainResult}
        onCreated={(created) => {
          setCreateOpen(false)
          setDraftQ('')
          setFilters({ ...DEFAULT_FILTERS })
          setPage(0)
          setFeedback({ variant: 'success', text: `${created.fullName} was created as ${ROLE_LABELS[created.role]}. Share the initial password securely outside UniDoc.` })
          refreshList()
        }} />}
      {dialog?.action === 'status' && <StatusDialog user={dialog.user} onClose={() => setDialog(null)} onUncertain={uncertainResult}
        onChanged={(updated) => {
          setDialog(null)
          setFeedback({ variant: 'success', text: `${updated.fullName} is now ${ACCOUNT_STATUS_LABELS[updated.status].toLowerCase()}. Existing sessions were revoked if the status changed.` })
          refreshList()
        }} />}
      {dialog?.action === 'reset' && <PasswordResetDialog user={dialog.user} onClose={() => setDialog(null)} onUncertain={uncertainResult}
        onReset={() => {
          const target = dialog.user
          setDialog(null)
          if (target.id === user.id) {
            logout()
            navigate('/login', { replace: true })
            return
          }
          setFeedback({ variant: 'success', text: `Password reset for ${target.fullName}. Existing sessions have been revoked. Communicate the new password securely outside UniDoc.` })
          refreshList()
        }} />}
    </>
  )
}
