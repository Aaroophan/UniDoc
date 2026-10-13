import { useEffect, useRef, useState } from 'react'
import { Link } from 'react-router-dom'
import type { PageResponse, NotificationType } from '../api/contracts'
import { NOTIFICATION_TYPES, NOTIFICATION_TYPE_LABELS } from '../api/contracts'
import { getApiError, getSafeErrorMessage } from '../api/errors'
import { FeedbackBanner } from '../components/FeedbackBanner'
import { PageHeader } from '../components/PageHeader'
import { PageState } from '../components/PageState'
import { Pagination } from '../components/Pagination'
import { useNotificationCount } from './NotificationCountContext'
import { listNotifications, markAllNotificationsRead, markNotificationRead, safeNotificationPath } from './notificationsApi'
import type { NotificationItem } from './notificationsApi'
import './notifications.css'

type ReadFilter = 'all' | 'unread' | 'read'
type LoadState = 'loading' | 'ready' | 'error' | 'refreshing' | 'stale'
type Feedback = { kind: 'error' | 'success' | 'warning'; message: string; requestId?: string | null }

const dateFormatter = new Intl.DateTimeFormat(undefined, { dateStyle: 'medium', timeStyle: 'short' })
function readableDate(value: string): string {
  const parsed = new Date(value)
  return Number.isNaN(parsed.getTime()) ? 'Date unavailable' : dateFormatter.format(parsed)
}

export function NotificationsPage() {
  const { unreadCount, refreshUnreadCount } = useNotificationCount()
  const [readFilter, setReadFilter] = useState<ReadFilter>('all')
  const [typeFilter, setTypeFilter] = useState<NotificationType | ''>('')
  const [page, setPage] = useState(0)
  const [reload, setReload] = useState(0)
  const [status, setStatus] = useState<LoadState>('loading')
  const [results, setResults] = useState<PageResponse<NotificationItem> | null>(null)
  const [loadingError, setLoadingError] = useState<{ message: string; requestId?: string | null } | null>(null)
  const [feedback, setFeedback] = useState<Feedback | null>(null)
  const [pending, setPending] = useState<number | 'all' | null>(null)
  const loadedKey = useRef<string | null>(null)
  const viewKey = `${readFilter}/${typeFilter}/${page}`

  useEffect(() => {
    const controller = new AbortController()
    async function load() {
      const sameResults = loadedKey.current === viewKey
      setStatus(sameResults ? 'refreshing' : 'loading')
      setLoadingError(null)
      try {
        const next = await listNotifications({
          read: readFilter === 'all' ? undefined : readFilter === 'read',
          type: typeFilter || undefined,
          page,
          size: 20,
        }, controller.signal)
        if (controller.signal.aborted) return
        if (next.totalPages > 0 && page >= next.totalPages) { setPage(next.totalPages - 1); return }
        setResults(next)
        loadedKey.current = viewKey
        setStatus('ready')
      } catch (failure: unknown) {
        if (controller.signal.aborted) return
        setLoadingError({ message: getSafeErrorMessage(failure, 'Unable to load notifications.'), requestId: getApiError(failure)?.requestId })
        setStatus(sameResults ? 'stale' : 'error')
      }
    }
    void load()
    return () => controller.abort()
  }, [readFilter, typeFilter, page, reload, viewKey])

  async function markOne(id: number) {
    setPending(id)
    setFeedback(null)
    try {
      await markNotificationRead(id)
      setFeedback({ kind: 'success', message: 'Notification marked as read.' })
    } catch (failure: unknown) {
      // POST may already have succeeded before a connection was lost. Refetch instead of retrying.
      setFeedback({ kind: 'warning', message: getSafeErrorMessage(failure, 'Unable to confirm the result. The list is being refreshed.'), requestId: getApiError(failure)?.requestId })
    } finally {
      setPending(null)
      setReload((n) => n + 1)
      void refreshUnreadCount()
    }
  }

  async function markAll() {
    setPending('all')
    setFeedback(null)
    try {
      await markAllNotificationsRead()
      setFeedback({ kind: 'success', message: 'All your notifications are marked as read.' })
    } catch (failure: unknown) {
      setFeedback({ kind: 'warning', message: getSafeErrorMessage(failure, 'Unable to confirm the result. The list is being refreshed.'), requestId: getApiError(failure)?.requestId })
    } finally {
      setPending(null)
      setReload((n) => n + 1)
      void refreshUnreadCount()
    }
  }

  function changeRead(value: ReadFilter) {
    setReadFilter(value)
    setPage(0)
    setFeedback(null)
  }

  function changeType(value: NotificationType | '') {
    setTypeFilter(value)
    setPage(0)
    setFeedback(null)
  }

  const isEmpty = (status === 'ready' || status === 'refreshing' || status === 'stale') && results?.items.length === 0

  return (
    <>
      <PageHeader eyebrow="Your updates" title="Notifications"
        description="Request updates are saved here even when email delivery is unavailable. Only you can view and mark your notifications."
        actions={<div className="d-flex flex-wrap gap-2 align-items-center">
          <button type="button" className="btn btn-outline-secondary" disabled={status === 'loading' || status === 'refreshing' || pending !== null}
            onClick={() => { setReload((n) => n + 1); void refreshUnreadCount() }}>Refresh</button>
          <button type="button" className="btn btn-primary" onClick={() => void markAll()}
            disabled={pending !== null || (status !== 'ready' && status !== 'stale')}>
            {pending === 'all' ? 'Marking...' : 'Mark all as read'}
          </button>
        </div>} />

      {feedback && <div className="mb-4"><FeedbackBanner variant={feedback.kind} message={feedback.message}
        requestId={feedback.requestId} onDismiss={() => setFeedback(null)} /></div>}

      <section className="surface-card notification-filter-card" aria-labelledby="notification-filters-heading">
        <div className="d-flex flex-wrap align-items-center justify-content-between gap-2 mb-3">
          <h2 id="notification-filters-heading" className="h5 mb-0">Filter notifications</h2>
          <span className="small text-muted" aria-live="polite">{unreadCount === null ? 'Unread count unavailable' : `${unreadCount} unread`}</span>
        </div>
        <div className="notification-filter-grid">
          <div>
            <label htmlFor="notifications-read-filter" className="form-label">Read status</label>
            <select className="form-select" id="notifications-read-filter" value={readFilter}
              onChange={(event) => changeRead(event.target.value as ReadFilter)}>
              <option value="all">All notifications</option>
              <option value="unread">Unread only</option>
              <option value="read">Read only</option>
            </select>
          </div>
          <div>
            <label htmlFor="notifications-type-filter" className="form-label">Event type</label>
            <select className="form-select" id="notifications-type-filter" value={typeFilter}
              onChange={(event) => changeType(event.target.value as NotificationType | '')}>
              <option value="">All event types</option>
              {NOTIFICATION_TYPES.map((type) => <option key={type} value={type}>{NOTIFICATION_TYPE_LABELS[type]}</option>)}
            </select>
          </div>
          <button type="button" className="btn btn-link notification-filter-reset"
            onClick={() => { changeRead('all'); changeType('') }}>Clear filters</button>
        </div>
      </section>

      {status === 'loading' && <PageState status="loading" message="Loading your notifications..." />}
      {status === 'refreshing' && <p role="status" className="small text-muted mb-3">Updating notifications…</p>}
      {status === 'stale' && <div role="alert" className="mb-3"><p className="request-inline-error">Unable to refresh notifications. Previous results remain visible. {loadingError?.message}</p><button type="button" className="btn btn-outline-primary btn-sm" onClick={() => setReload((n) => n + 1)}>Try again</button></div>}
      {status === 'error' && <PageState status="error" title="Notifications unavailable"
        message={loadingError?.message ?? 'Unable to load your notifications.'} requestId={loadingError?.requestId}
        onRetry={() => setReload((n) => n + 1)} />}
      {isEmpty && <PageState status="empty" title={readFilter === 'unread' ? 'You are all caught up' : readFilter === 'read' ? 'No read notifications' : 'No notifications yet'}
        message={readFilter === 'unread' ? 'There are no unread updates matching your filters.' : readFilter === 'read' ? 'Read notifications will appear here after you mark them as read.' : typeFilter ? 'No notifications match this event type.' : 'Updates about your document requests will appear here as requests are processed.'} />}
      {(status === 'ready' || status === 'refreshing' || status === 'stale') && results && results.items.length > 0 && (
        <section className="notification-list" aria-label="Your notifications">
          {results.items.map((item) => {
            const action = safeNotificationPath(item.actionPath)
            return <article className={`surface-card notification-entry${item.read ? '' : ' is-unread'}`} key={item.id}
              aria-labelledby={`notification-${item.id}`}>
              <div className="notification-entry-heading">
                <div className="min-width-0">
                  <p className="notification-entry-type">{NOTIFICATION_TYPE_LABELS[item.type] ?? 'Request update'}{!item.read && <span className="notification-unread-text"> · Unread</span>}</p>
                  <h2 id={`notification-${item.id}`} className="h5 notification-entry-title">{item.title}</h2>
                </div>
                <time className="notification-entry-time" dateTime={item.createdAt}>{readableDate(item.createdAt)}</time>
              </div>
              <p className="notification-entry-message">{item.message}</p>
              <div className="notification-entry-actions">
                {action && <Link className="btn btn-outline-primary btn-sm" to={action}>View related page</Link>}
                {!item.read && <button type="button" className="btn btn-outline-secondary btn-sm"
                  disabled={pending !== null} onClick={() => void markOne(item.id)}>
                  {pending === item.id ? 'Marking...' : 'Mark as read'}</button>}
                {item.read && item.readAt && <span className="small text-muted">Read {readableDate(item.readAt)}</span>}
              </div>
            </article>
          })}
          <Pagination page={results} onPageChange={setPage} disabled={pending !== null} />
        </section>
      )}
    </>
  )
}
