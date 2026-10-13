import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { DELIVERY_METHOD_LABELS } from '../api/contracts'
import { getApiError, getSafeErrorMessage } from '../api/errors'
import { PageHeader } from '../components/PageHeader'
import { PageState } from '../components/PageState'
import { RequestStatus as RequestStatusBadge } from '../components/RequestStatus'
import { formatRequestTime } from '../requests/requestFormat'
import { getAdminDashboard } from './adminInsightsApi'
import type { AdminDashboard } from './adminInsightsApi'
import './adminInsights.css'

const STAT_CARDS: { key: keyof Pick<AdminDashboard, 'totalRequests' | 'pendingRequests' | 'approvedRequests' | 'rejectedRequests' | 'readyRequests' | 'completedRequests' | 'processingRequests'>; title: string; description: string }[] = [
  { key: 'totalRequests', title: 'All requests', description: 'Every submitted request' },
  { key: 'pendingRequests', title: 'Pending review', description: 'Submitted, under review, or awaiting information' },
  { key: 'approvedRequests', title: 'Approved', description: 'Approved, not yet processing' },
  { key: 'readyRequests', title: 'Ready', description: 'Ready for collection or download' },
  { key: 'completedRequests', title: 'Completed', description: 'Delivery completed' },
  { key: 'rejectedRequests', title: 'Rejected', description: 'Terminal rejections' },
]

export function AdminDashboardPage() {
  const [data, setData] = useState<AdminDashboard | null>(null)
  const [state, setState] = useState<'loading' | 'ready' | 'error' | 'refreshing' | 'stale'>('loading')
  const [error, setError] = useState<{ message: string; requestId?: string | null } | null>(null)
  const [reload, setReload] = useState(0)
  useEffect(() => {
    const controller = new AbortController()
    getAdminDashboard(controller.signal).then((dashboard) => {
      if (!controller.signal.aborted) { setData(dashboard); setState('ready') }
    }).catch((failure: unknown) => {
      if (!controller.signal.aborted) {
        setError({ message: getSafeErrorMessage(failure, 'Unable to load dashboard.'), requestId: getApiError(failure)?.requestId })
        setState((current) => current === 'refreshing' ? 'stale' : 'error')
      }
    })
    return () => controller.abort()
  }, [reload])

  return <>
    <PageHeader eyebrow="Administration" title="Request dashboard" description="Current request activity across the university. Statistics use committed workflow states."
      actions={<button type="button" className="btn btn-outline-primary" disabled={state === 'loading' || state === 'refreshing'} onClick={() => { setState(data ? 'refreshing' : 'loading'); setReload((n) => n + 1) }}>Refresh</button>} />
    {state === 'loading' && <PageState status="loading" message="Calculating request statistics..." />}
    {state === 'refreshing' && <p role="status" className="small text-muted mb-3">Refreshing dashboard statistics…</p>}
    {state === 'stale' && <div role="alert" className="mb-3"><p className="request-inline-error">Unable to refresh the dashboard. Previous counts are shown and may be out of date. {error?.message}</p><button className="btn btn-outline-primary btn-sm" onClick={() => { setState('refreshing'); setReload((n) => n + 1) }}>Try again</button></div>}
    {state === 'error' && <PageState status="error" title="Unable to load dashboard" message={error?.message ?? 'Please retry.'}
      requestId={error?.requestId} onRetry={() => { setState('loading'); setReload((n) => n + 1) }} />}
    {(state === 'ready' || state === 'refreshing' || state === 'stale') && data && <>
      <section aria-label="Request statistics" className="admin-stat-grid">
        {STAT_CARDS.map((metric) => <div className="surface-card admin-stat-card" key={metric.key}>
          <p className="section-eyebrow mb-2">{metric.title}</p>
          <p className="admin-stat-value">{data[metric.key].toLocaleString()}</p>
          <p className="small text-muted mb-0">{metric.description}</p>
        </div>)}
      </section>
      <p className="small text-muted mt-2 mb-4">Requests in processing: <strong>{data.processingRequests.toLocaleString()}</strong>. Pending includes exactly the three review-related states.</p>
      <div className="admin-analytics-grid">
        <section className="surface-card request-detail-section" aria-labelledby="admin-delivery-heading">
          <h2 id="admin-delivery-heading" className="h5 mb-3">Delivery methods</h2>
          {(['ELECTRONIC', 'PHYSICAL'] as const).map((method) => <div className="admin-breakdown-row" key={method}>
            <span>{DELIVERY_METHOD_LABELS[method]}</span><strong>{data.byDeliveryMethod[method].toLocaleString()}</strong>
          </div>)}
        </section>
        <section className="surface-card request-detail-section" aria-labelledby="admin-type-heading">
          <h2 id="admin-type-heading" className="h5 mb-3">Requests by document type</h2>
          {data.byDocumentType.length === 0 ? <p className="small text-muted mb-0">No requests recorded yet.</p> :
            data.byDocumentType.map((group) => <div className="admin-breakdown-row" key={`${group.documentTypeId}-${group.documentTypeName}`}>
              <span className="request-wrap">{group.documentTypeName}</span><strong>{group.count.toLocaleString()}</strong>
            </div>)}
        </section>
      </div>
      <section className="surface-card request-detail-section mt-4" aria-labelledby="admin-recent-heading">
        <div className="d-flex flex-wrap justify-content-between align-items-center gap-2 mb-3">
          <h2 className="h5 mb-0" id="admin-recent-heading">Recent requests</h2>
          <Link className="btn btn-outline-primary btn-sm" to="/admin/requests">Monitor all requests</Link>
        </div>
        {data.recentRequests.length === 0 ? <PageState status="empty" title="No requests yet" message="New student requests will appear here as they are submitted." /> :
          <div className="admin-recent-list">{data.recentRequests.map((row) => <div className="admin-recent-row" key={row.id}>
            <div><Link className="fw-semibold" to={`/admin/requests/${row.id}`}>{row.requestNumber}</Link>
              <div className="small text-muted request-wrap">{row.studentName} · {row.documentTypeName} · {formatRequestTime(row.createdAt)}</div></div>
            <RequestStatusBadge status={row.status} />
          </div>)}</div>}
      </section>
    </>}
  </>
}
