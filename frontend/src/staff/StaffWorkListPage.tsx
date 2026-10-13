import { useEffect, useMemo, useState } from 'react'
import type { FormEvent } from 'react'
import { Link, useLocation, useSearchParams } from 'react-router-dom'
import { DELIVERY_METHODS, DELIVERY_METHOD_LABELS, REQUEST_STATUSES, REQUEST_STATUS_LABELS } from '../api/contracts'
import type { PageResponse, RequestStatus, DeliveryMethod } from '../api/contracts'
import { getApiError, getSafeErrorMessage } from '../api/errors'
import { listActiveDocumentTypes } from '../catalogue/documentTypesApi'
import type { CatalogueDocumentType } from '../catalogue/documentTypesApi'
import { PageHeader } from '../components/PageHeader'
import { PageState } from '../components/PageState'
import { Pagination } from '../components/Pagination'
import { RequestStatus as RequestStatusBadge } from '../components/RequestStatus'
import { formatRequestDate, formatRequestTime } from '../requests/requestFormat'
import { listStaffRequests } from './staffRequestsApi'
import { listAdminRequests } from '../admin/adminInsightsApi'
import type { StaffRequestFilters, StaffRequestSort, StaffRequestSummary } from './staffRequestsApi'
import './staff.css'

type LoadState = 'loading' | 'error' | 'ready'
const SORTS: StaffRequestSort[] = ['createdAt', 'updatedAt', 'requiredDate', 'status', 'requestNumber']

function safePage(value: string | null): number {
  if (!value || !/^(0|[1-9][0-9]*)$/.test(value)) return 0
  const number = Number(value)
  return Number.isSafeInteger(number) && number <= 1000000 ? number : 0
}

function readFilters(params: URLSearchParams): StaffRequestFilters {
  const status = params.get('status') ?? ''
  const method = params.get('deliveryMethod') ?? ''
  const typeId = params.get('documentTypeId')
  const typeNumber = typeId && /^[1-9][0-9]*$/.test(typeId) ? Number(typeId) : undefined
  const sort = params.get('sort') ?? ''
  const direction = params.get('direction') ?? ''
  return {
    q: params.get('q') || undefined,
    status: REQUEST_STATUSES.includes(status as RequestStatus) ? status as RequestStatus : undefined,
    documentTypeId: typeNumber && Number.isSafeInteger(typeNumber) ? typeNumber : undefined,
    deliveryMethod: DELIVERY_METHODS.includes(method as DeliveryMethod) ? method as DeliveryMethod : undefined,
    submittedFrom: params.get('submittedFrom') || undefined,
    submittedTo: params.get('submittedTo') || undefined,
    requiredDate: params.get('requiredDate') || undefined,
    sort: SORTS.includes(sort as StaffRequestSort) ? sort as StaffRequestSort : 'createdAt',
    direction: direction === 'asc' ? 'asc' : 'desc',
    page: safePage(params.get('page')),
    size: 10,
  }
}

function StaffFilters({ activeTypes, selected, onApply, onClear, isFiltered, mode }: {
  activeTypes: CatalogueDocumentType[]
  selected: StaffRequestFilters
  onApply: (filters: StaffRequestFilters) => void
  onClear: () => void
  isFiltered: boolean
  mode: 'staff' | 'admin'
}) {
  const [validation, setValidation] = useState<string | null>(null)
  const selectedId = selected.documentTypeId
  const missingType = selectedId && !activeTypes.some((type) => type.id === selectedId)

  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const values = new FormData(event.currentTarget)
    const get = (key: string) => String(values.get(key) || '').trim()
    const from = get('submittedFrom')
    const to = get('submittedTo')
    if (from && to && from > to) {
      setValidation('Submitted from cannot be later than submitted to.')
      return
    }
    setValidation(null)
    onApply({
      q: get('q'),
      status: get('status') as RequestStatus | '',
      documentTypeId: get('documentTypeId') ? Number(get('documentTypeId')) : undefined,
      deliveryMethod: get('deliveryMethod') as DeliveryMethod | '',
      submittedFrom: from,
      submittedTo: to,
      requiredDate: get('requiredDate'),
      sort: get('sort') as StaffRequestSort,
      direction: get('direction') as 'asc' | 'desc',
    })
  }

  return (
    <form className="surface-card request-filters staff-filters" aria-label={mode === 'admin' ? 'Filter monitored requests' : 'Filter staff requests'} onSubmit={submit}>
      <div className="request-filters-header">
        <div><h2 className="h5 mb-1">Find requests</h2><p className="small text-muted mb-0">{mode === 'admin' ? 'Monitor submitted requests across all students without changing their status.' : 'The same shared work list is visible to every active staff member.'}</p></div>
        <button type="button" className="btn btn-outline-secondary btn-sm" disabled={!isFiltered} onClick={onClear}>Clear all</button>
      </div>
      <div className="staff-filter-grid">
        <div className="staff-filter-search">
          <label className="form-label" htmlFor="staff-filter-q">Search requests and students</label>
          <input id="staff-filter-q" name="q" type="search" className="form-control" maxLength={120} defaultValue={selected.q ?? ''}
            placeholder="Request number, name, email, registration" />
        </div>
        <div>
          <label className="form-label" htmlFor="staff-filter-status">Status</label>
          <select id="staff-filter-status" name="status" className="form-select" defaultValue={selected.status ?? ''}>
            <option value="">All statuses</option>
            {REQUEST_STATUSES.map((status) => <option key={status} value={status}>{REQUEST_STATUS_LABELS[status]}</option>)}
          </select>
        </div>
        <div>
          <label className="form-label" htmlFor="staff-filter-document">Document type</label>
          <select id="staff-filter-document" name="documentTypeId" className="form-select" defaultValue={selectedId ?? ''}>
            <option value="">All document types</option>
            {missingType && <option value={selectedId}>Previously selected type ({selectedId})</option>}
            {activeTypes.map((type) => <option key={type.id} value={type.id}>{type.name}</option>)}
          </select>
        </div>
        <div>
          <label className="form-label" htmlFor="staff-filter-method">Delivery method</label>
          <select id="staff-filter-method" name="deliveryMethod" className="form-select" defaultValue={selected.deliveryMethod ?? ''}>
            <option value="">All methods</option>
            {DELIVERY_METHODS.map((method) => <option key={method} value={method}>{DELIVERY_METHOD_LABELS[method]}</option>)}
          </select>
        </div>
        <div>
          <label className="form-label" htmlFor="staff-filter-from">Submitted from (inclusive)</label>
          <input id="staff-filter-from" name="submittedFrom" type="date" className="form-control" defaultValue={selected.submittedFrom ?? ''} />
        </div>
        <div>
          <label className="form-label" htmlFor="staff-filter-to">Submitted to (inclusive)</label>
          <input id="staff-filter-to" name="submittedTo" type="date" className="form-control" defaultValue={selected.submittedTo ?? ''} />
        </div>
        <div>
          <label className="form-label" htmlFor="staff-filter-required">Required date</label>
          <input id="staff-filter-required" name="requiredDate" type="date" className="form-control" defaultValue={selected.requiredDate ?? ''} />
        </div>
        <div>
          <label className="form-label" htmlFor="staff-filter-sort">Sort by</label>
          <select id="staff-filter-sort" name="sort" className="form-select" defaultValue={selected.sort ?? 'createdAt'}>
            <option value="createdAt">Submitted</option>
            <option value="updatedAt">Last updated</option>
            <option value="requiredDate">Required date</option>
            <option value="status">Status</option>
            <option value="requestNumber">Request number</option>
          </select>
        </div>
        <div>
          <label className="form-label" htmlFor="staff-filter-direction">Order</label>
          <select id="staff-filter-direction" name="direction" className="form-select" defaultValue={selected.direction ?? 'desc'}>
            <option value="desc">Descending</option>
            <option value="asc">Ascending</option>
          </select>
        </div>
      </div>
      {validation && <p className="request-inline-error mt-3" role="alert">{validation}</p>}
      <div className="d-flex justify-content-end mt-4">
        <button className="btn btn-primary" type="submit">Apply filters</button>
      </div>
    </form>
  )
}

export function StaffWorkListPage({ mode = 'staff' }: { mode?: 'staff' | 'admin' } = {}) {
  const location = useLocation()
  const [params, setParams] = useSearchParams()
  const query = params.toString()
  const filters = useMemo(() => readFilters(new URLSearchParams(query)), [query])
  const filterKey = useMemo(() => {
    const copied = new URLSearchParams(query)
    copied.delete('page')
    return copied.toString()
  }, [query])
  const filtered = Boolean(filterKey)
  const [activeTypes, setActiveTypes] = useState<CatalogueDocumentType[]>([])
  const [catalogueError, setCatalogueError] = useState<string | null>(null)
  const [results, setResults] = useState<PageResponse<StaffRequestSummary> | null>(null)
  const [loadState, setLoadState] = useState<LoadState>('loading')
  const [loadError, setLoadError] = useState<{ message: string; requestId?: string | null } | null>(null)
  const [reload, setReload] = useState(0)

  useEffect(() => {
    const controller = new AbortController()
    listActiveDocumentTypes(controller.signal).then((types) => {
      if (!controller.signal.aborted) setActiveTypes(types)
    }).catch(() => {
      if (!controller.signal.aborted) setCatalogueError('Document type choices could not be loaded. Other filters remain available.')
    })
    return () => controller.abort()
  }, [])

  useEffect(() => {
    const controller = new AbortController()
    async function load() {
      setLoadState('loading')
      setLoadError(null)
      try {
        const response = await (mode === 'admin' ? listAdminRequests(filters, controller.signal) : listStaffRequests(filters, controller.signal))
        if (controller.signal.aborted) return
        if ((response.totalPages > 0 && response.page >= response.totalPages) ||
            (response.totalPages === 0 && response.page !== 0)) {
          setParams((old) => {
            const next = new URLSearchParams(old)
            const target = Math.max(response.totalPages - 1, 0)
            if (target > 0) next.set('page', String(target))
            else next.delete('page')
            return next
          }, { replace: true })
          return
        }
        setResults(response)
        setLoadState('ready')
      } catch (error: unknown) {
        if (controller.signal.aborted) return
        setLoadError({ message: getSafeErrorMessage(error, 'Unable to load the staff work list.'), requestId: getApiError(error)?.requestId })
        setLoadState('error')
      }
    }
    void load()
    return () => controller.abort()
  }, [filters, reload, setParams, mode])

  function applyFilters(next: StaffRequestFilters) {
    const values = new URLSearchParams()
    for (const [key, value] of Object.entries(next)) {
      if (value !== undefined && value !== null && value !== '' &&
          !(key === 'sort' && value === 'createdAt') && !(key === 'direction' && value === 'desc')) {
        values.set(key, String(value))
      }
    }
    setParams(values)
    if (values.toString() === query) setReload((value) => value + 1)
  }

  function pageTo(value: number) {
    setParams((previous) => {
      const next = new URLSearchParams(previous)
      if (value === 0) next.delete('page')
      else next.set('page', String(value))
      return next
    })
  }

  return (
    <>
      <PageHeader eyebrow={mode === 'admin' ? 'Administration' : 'Staff workspace'} title={mode === 'admin' ? 'Request monitoring' : 'Request work list'}
        description={mode === 'admin' ? 'Read-only oversight of student requests, delivery progress, and history. Workflow actions remain staff-only.' : 'Find and review document requests across all students. No assignment or claim step is required.'}
        actions={<button type="button" className="btn btn-outline-primary" disabled={loadState === 'loading'} onClick={() => setReload((value) => value + 1)}>Refresh</button>} />
      {catalogueError && <p role="status" className="small text-muted">{catalogueError}</p>}
      <StaffFilters key={filterKey} selected={filters} activeTypes={activeTypes}
        mode={mode} isFiltered={filtered || safePage(params.get('page')) > 0} onApply={applyFilters} onClear={() => setParams(new URLSearchParams())} />
      <section className="surface-card request-results" aria-labelledby="staff-results-title">
        <div className="request-results-header">
          <div><p className="section-eyebrow mb-2">University requests</p><h2 className="h5 mb-0" id="staff-results-title">{mode === 'admin' ? 'All requests' : 'Shared work list'}</h2></div>
          {results && loadState === 'ready' && <span className="small text-muted">{results.totalItems} matching requests</span>}
        </div>
        {loadState === 'loading' && <PageState status="loading" message="Loading student requests..." />}
        {loadState === 'error' && <PageState status="error" title="Unable to load the request list"
          message={loadError?.message ?? 'Please try again.'} requestId={loadError?.requestId}
          onRetry={() => setReload((value) => value + 1)} />}
        {loadState === 'ready' && results?.items.length === 0 && <PageState status="empty"
          title={filtered ? 'No matching requests' : 'No document requests yet'}
          message={filtered ? 'Try a different search, status, date, or document type.' : 'Submitted student requests will appear here.'}
          action={filtered ? <button type="button" className="btn btn-outline-primary" onClick={() => setParams(new URLSearchParams())}>Clear filters</button> : undefined} />}
        {loadState === 'ready' && results && results.items.length > 0 && <>
          <div className="request-results-list">
            {results.items.map((item) => <article key={item.id} className="request-result-row staff-result-row">
              <div className="request-result-identity">
                <p className="request-ref mb-2">{item.requestNumber}</p>
                <h3 className="h6 mb-2 request-wrap">{item.documentTypeName}</h3>
                <p className="request-inline-meta mb-1 request-wrap">{item.studentName}</p>
                <p className="request-inline-meta mb-0 request-wrap">{item.registrationNumber || 'Registration not recorded'}</p>
              </div>
              <div className="request-result-facts">
                <div><span className="request-fact-label">Status</span><RequestStatusBadge status={item.status} /></div>
                <div><span className="request-fact-label">Delivery</span><strong>{DELIVERY_METHOD_LABELS[item.deliveryMethod]}</strong></div>
                <div><span className="request-fact-label">Required by</span><strong>{formatRequestDate(item.requiredDate)}</strong></div>
                <div><span className="request-fact-label">Submitted</span><strong>{formatRequestTime(item.createdAt)}</strong></div>
                <div><span className="request-fact-label">Updated</span><strong>{formatRequestTime(item.updatedAt)}</strong></div>
              </div>
              <Link className="btn btn-outline-primary btn-sm request-result-action" to={`${mode === 'admin' ? '/admin' : '/staff'}/requests/${item.id}`}
                state={{ from: location.pathname + location.search }} aria-label={`${mode === 'admin' ? 'Monitor' : 'Review'} request ${item.requestNumber}`}>View details</Link>
            </article>)}
          </div>
          <div className="request-results-pagination"><Pagination page={results} onPageChange={pageTo} /></div>
        </>}
      </section>
    </>
  )
}
