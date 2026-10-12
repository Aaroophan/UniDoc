import { useEffect, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { DELIVERY_METHOD_LABELS, DELIVERY_METHODS, REQUEST_STATUSES, REQUEST_STATUS_LABELS } from '../api/contracts'
import type { DeliveryMethod, PageResponse, RequestStatus } from '../api/contracts'
import { getApiError, getSafeErrorMessage } from '../api/errors'
import { PageHeader } from '../components/PageHeader'
import { PageState } from '../components/PageState'
import { Pagination } from '../components/Pagination'
import { RequestStatus as RequestStatusBadge } from '../components/RequestStatus'
import { formatRequestDate, formatRequestTime } from './requestFormat'
import { listStudentRequests } from './requestsApi'
import type { StudentRequestSummary } from './requestsApi'
import './requests.css'

type LoadState = 'loading' | 'error' | 'ready'

function safePage(value: string | null): number {
  if (!value || !/^(0|[1-9][0-9]*)$/.test(value)) return 0
  const parsed = Number(value)
  return Number.isSafeInteger(parsed) && parsed <= 1000000 ? parsed : 0
}

export function MyRequestsPage() {
  const [searchParams, setSearchParams] = useSearchParams()
  const statusValue = searchParams.get('status') ?? ''
  const methodValue = searchParams.get('deliveryMethod') ?? ''
  const status = REQUEST_STATUSES.includes(statusValue as RequestStatus) ? statusValue as RequestStatus : ''
  const deliveryMethod = DELIVERY_METHODS.includes(methodValue as DeliveryMethod) ? methodValue as DeliveryMethod : ''
  const page = safePage(searchParams.get('page'))
  const filtered = Boolean(status || deliveryMethod)

  const [loadState, setLoadState] = useState<LoadState>('loading')
  const [results, setResults] = useState<PageResponse<StudentRequestSummary> | null>(null)
  const [loadError, setLoadError] = useState<{ message: string; requestId?: string | null } | null>(null)
  const [reload, setReload] = useState(0)

  function setFilter(name: 'status' | 'deliveryMethod', value: string) {
    setSearchParams((previous) => {
      const next = new URLSearchParams(previous)
      if (value) next.set(name, value)
      else next.delete(name)
      next.delete('page')
      return next
    })
  }

  function setPage(nextPage: number, replace = false) {
    if (!Number.isInteger(nextPage) || nextPage < 0) return
    setSearchParams((previous) => {
      const next = new URLSearchParams(previous)
      if (nextPage) next.set('page', String(nextPage))
      else next.delete('page')
      return next
    }, { replace })
  }

  useEffect(() => {
    const controller = new AbortController()
    async function load() {
      setLoadState('loading')
      setLoadError(null)
      try {
        const result = await listStudentRequests({
          status: status || undefined,
          deliveryMethod: deliveryMethod || undefined,
          page,
          size: 10,
          sort: 'createdAt',
          direction: 'desc',
        }, controller.signal)
        if (controller.signal.aborted) return
        if (result.totalPages > 0 && page >= result.totalPages) {
          setPage(result.totalPages - 1, true)
          return
        }
        if (result.totalPages === 0 && page !== 0) {
          setPage(0, true)
          return
        }
        setResults(result)
        setLoadState('ready')
      } catch (failure: unknown) {
        if (controller.signal.aborted) return
        setLoadError({ message: getSafeErrorMessage(failure, 'Unable to load your requests.'), requestId: getApiError(failure)?.requestId })
        setLoadState('error')
      }
    }
    void load()
    return () => controller.abort()
  }, [status, deliveryMethod, page, reload])

  function clearFilters() {
    setSearchParams(new URLSearchParams())
  }

  return (
    <>
      <PageHeader eyebrow="Student workspace" title="My requests"
        description="Follow every document request from submission through delivery."
        actions={<Link className="btn btn-primary" to="/catalogue">Request a document</Link>} />
      <section className="surface-card request-filters" aria-label="Filter requests">
        <div className="request-filters-header">
          <div><h2 className="h5 mb-1">Find a request</h2><p className="small text-muted mb-0">Your requests are displayed newest first.</p></div>
          <button type="button" onClick={clearFilters} className="btn btn-outline-secondary btn-sm" disabled={!filtered && page === 0}>Clear filters</button>
        </div>
        <div className="request-filter-grid">
          <div>
            <label className="form-label" htmlFor="request-status-filter">Status</label>
            <select id="request-status-filter" className="form-select" value={status} onChange={(event) => setFilter('status', event.target.value)}>
              <option value="">All statuses</option>
              {REQUEST_STATUSES.map((value) => <option key={value} value={value}>{REQUEST_STATUS_LABELS[value]}</option>)}
            </select>
          </div>
          <div>
            <label className="form-label" htmlFor="request-delivery-filter">Delivery method</label>
            <select id="request-delivery-filter" className="form-select" value={deliveryMethod} onChange={(event) => setFilter('deliveryMethod', event.target.value)}>
              <option value="">All delivery methods</option>
              {DELIVERY_METHODS.map((value) => <option key={value} value={value}>{DELIVERY_METHOD_LABELS[value]}</option>)}
            </select>
          </div>
        </div>
      </section>
      <section className="surface-card request-results" aria-labelledby="request-results-title">
        <div className="request-results-header">
          <div>
            <p className="section-eyebrow mb-2">Request history</p>
            <h2 className="h5 mb-0" id="request-results-title">Your document requests</h2>
          </div>
          <button type="button" className="btn btn-outline-secondary btn-sm" disabled={loadState === 'loading'} onClick={() => setReload((value) => value + 1)}>Refresh</button>
        </div>
        {loadState === 'loading' && <PageState status="loading" message="Loading your requests..." />}
        {loadState === 'error' && <PageState status="error" title="Unable to load requests"
          message={loadError?.message ?? 'Try refreshing your requests.'} requestId={loadError?.requestId}
          onRetry={() => setReload((value) => value + 1)} />}
        {loadState === 'ready' && results && results.items.length === 0 && (
          <PageState status="empty" title={filtered ? 'No matching requests' : 'No requests submitted yet'}
            message={filtered ? 'No requests match these filters. Try another status or delivery method.' : 'Start with the document catalogue, choose a document, and submit your first request.'}
            action={filtered ? <button type="button" onClick={clearFilters} className="btn btn-outline-primary">Clear filters</button>
              : <Link to="/catalogue" className="btn btn-primary">Browse documents</Link>} />
        )}
        {loadState === 'ready' && results && results.items.length > 0 && (
          <>
            <div className="request-results-list">
              {results.items.map((request) => (
                <article className="request-result-row" key={request.id} aria-labelledby={`request-card-${request.id}`}>
                  <div className="request-result-identity">
                    <p className="request-ref mb-2">{request.requestNumber}</p>
                    <h3 className="h6 mb-2 request-wrap" id={`request-card-${request.id}`}>{request.documentTypeName}</h3>
                    <div className="request-inline-meta">{DELIVERY_METHOD_LABELS[request.deliveryMethod]} delivery</div>
                  </div>
                  <div className="request-result-facts">
                    <div><span className="request-fact-label">Status</span><RequestStatusBadge status={request.status} /></div>
                    <div><span className="request-fact-label">Required by</span><strong>{formatRequestDate(request.requiredDate)}</strong></div>
                    <div><span className="request-fact-label">Submitted</span><strong>{formatRequestTime(request.createdAt)}</strong></div>
                    <div><span className="request-fact-label">Last updated</span><strong>{formatRequestTime(request.updatedAt)}</strong></div>
                  </div>
                  <Link className="btn btn-outline-primary btn-sm request-result-action" to={`/requests/${request.id}`} aria-label={`View request ${request.requestNumber}`}>View details</Link>
                </article>
              ))}
            </div>
            <div className="request-results-pagination"><Pagination page={results} onPageChange={(newPage) => setPage(newPage)} /></div>
          </>
        )}
      </section>
    </>
  )
}
