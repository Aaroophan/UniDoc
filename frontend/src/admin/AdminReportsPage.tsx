import { useEffect, useMemo, useRef, useState } from 'react'
import type { FormEvent } from 'react'
import { DELIVERY_METHODS, DELIVERY_METHOD_LABELS, REQUEST_STATUSES, REQUEST_STATUS_LABELS } from '../api/contracts'
import type { DeliveryMethod, PageResponse, RequestStatus } from '../api/contracts'
import { getApiError, getSafeErrorMessage } from '../api/errors'
import { listAdminDocumentTypes } from '../catalogue/documentTypesApi'
import type { AdminDocumentType } from '../catalogue/documentTypesApi'
import { PageHeader } from '../components/PageHeader'
import { focusFirstInvalidField } from '../components/formFocus'
import { PageState } from '../components/PageState'
import { Pagination } from '../components/Pagination'
import { RequestStatus as RequestStatusBadge } from '../components/RequestStatus'
import { formatRequestDate, formatRequestTime } from '../requests/requestFormat'
import { useSearchParams } from 'react-router-dom'
import { listRequestReport, saveRequestReportCsv } from './adminInsightsApi'
import type { RequestReportFilters, RequestReportRow, RequestReportSort } from './adminInsightsApi'
import { appliedReportUrl, readReportFilters } from './reportFilters'
import './adminInsights.css'

type LoadState = 'loading' | 'error' | 'ready' | 'refreshing' | 'stale'

function ReportForm({ applied, documentTypes, onApply, onClear }: {
  applied: RequestReportFilters
  documentTypes: AdminDocumentType[]
  onApply: (filters: RequestReportFilters) => void
  onClear: () => void
}) {
  const [validation, setValidation] = useState<string | null>(null)
  const formRef = useRef<HTMLFormElement>(null)
  const missingType = applied.documentTypeId && !documentTypes.some((type) => type.id === applied.documentTypeId)
  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const form = new FormData(event.currentTarget)
    const get = (name: string) => String(form.get(name) ?? '').trim()
    const from = get('submittedFrom')
    const to = get('submittedTo')
    if (from && to && from > to) {
      setValidation('The submitted-from date cannot be later than the submitted-to date.')
      focusFirstInvalidField(event.currentTarget, ['submittedTo'])
      return
    }
    setValidation(null)
    onApply({
      submittedFrom: from, submittedTo: to,
      status: get('status') as RequestStatus | '',
      documentTypeId: get('documentTypeId') ? Number(get('documentTypeId')) : undefined,
      deliveryMethod: get('deliveryMethod') as DeliveryMethod | '',
      sort: get('sort') as RequestReportSort,
      direction: get('direction') as 'asc' | 'desc',
    })
  }
  return <form ref={formRef} className="surface-card request-filters staff-filters" aria-label="Filter administrator report" onSubmit={submit}>
    <div className="request-filters-header"><div>
      <h2 className="h5 mb-1">Report filters</h2>
      <p className="small text-muted mb-0">Dates refer to when the request was submitted. Apply filters before exporting.</p>
    </div><button className="btn btn-outline-secondary btn-sm" type="button" onClick={() => { setValidation(null); formRef.current?.reset(); onClear() }}>Clear all</button></div>
    <div className="admin-report-filter-grid">
      <div><label className="form-label" htmlFor="report-from">Submitted from (inclusive)</label>
        <input className="form-control" id="report-from" name="submittedFrom" type="date" defaultValue={applied.submittedFrom ?? ''} /></div>
      <div><label className="form-label" htmlFor="report-to">Submitted to (inclusive)</label>
        <input className="form-control" id="report-to" name="submittedTo" type="date" aria-invalid={Boolean(validation)} aria-describedby={validation ? 'report-date-error' : undefined} defaultValue={applied.submittedTo ?? ''} /></div>
      <div><label className="form-label" htmlFor="report-status">Status</label>
        <select className="form-select" id="report-status" name="status" defaultValue={applied.status ?? ''}>
          <option value="">All statuses</option>
          {REQUEST_STATUSES.map((status) => <option key={status} value={status}>{REQUEST_STATUS_LABELS[status]}</option>)}
        </select></div>
      <div><label className="form-label" htmlFor="report-type">Document type</label>
        <select className="form-select" id="report-type" name="documentTypeId" defaultValue={applied.documentTypeId ?? ''}>
          <option value="">All document types</option>
          {missingType && <option value={applied.documentTypeId}>Previously selected type ({applied.documentTypeId})</option>}
          {documentTypes.map((type) => <option key={type.id} value={type.id}>{type.name}{type.active ? '' : ' (inactive)'}</option>)}
        </select></div>
      <div><label className="form-label" htmlFor="report-method">Delivery method</label>
        <select className="form-select" id="report-method" name="deliveryMethod" defaultValue={applied.deliveryMethod ?? ''}>
          <option value="">All methods</option>
          {DELIVERY_METHODS.map((method) => <option key={method} value={method}>{DELIVERY_METHOD_LABELS[method]}</option>)}
        </select></div>
      <div><label className="form-label" htmlFor="report-sort">Sort by</label>
        <select className="form-select" id="report-sort" name="sort" defaultValue={applied.sort ?? 'createdAt'}>
          <option value="createdAt">Submission date</option>
          <option value="updatedAt">Last updated</option>
          <option value="completedAt">Completed date</option>
          <option value="requiredDate">Required date</option>
          <option value="requestNumber">Request number</option>
          <option value="status">Status</option>
          <option value="deliveryMethod">Delivery method</option>
        </select></div>
      <div><label className="form-label" htmlFor="report-direction">Order</label>
        <select className="form-select" id="report-direction" name="direction" defaultValue={applied.direction ?? 'desc'}>
          <option value="desc">Descending</option><option value="asc">Ascending</option>
        </select></div>
    </div>
    {validation && <p role="alert" id="report-date-error" className="request-inline-error mt-3">{validation}</p>}
    <div className="d-flex justify-content-end mt-3"><button className="btn btn-primary" type="submit">Apply filters</button></div>
  </form>
}

function ReportTable({ rows }: { rows: RequestReportRow[] }) {
  return <div className="table-responsive" role="region" aria-label="Filtered request report (scroll horizontally to view all columns)" tabIndex={0}>
    <table className="table table-hover admin-report-table">
      <thead><tr>
        <th scope="col">Request number</th><th scope="col">Registration</th><th scope="col">Student name</th>
        <th scope="col">Student email</th><th scope="col">Document type</th><th scope="col">Purpose</th>
        <th scope="col">Delivery</th><th scope="col">Required date</th><th scope="col">Status</th>
        <th scope="col">Submitted</th><th scope="col">Updated</th><th scope="col">Completed</th>
      </tr></thead>
      <tbody>{rows.map((row) => <tr key={row.requestNumber}>
        <th scope="row">{row.requestNumber}</th><td className="admin-report-cell">{row.registrationNumber || '—'}</td>
        <td className="admin-report-cell">{row.studentName}</td><td className="admin-report-cell">{row.studentEmail}</td>
        <td className="admin-report-cell">{row.documentTypeName}</td><td className="admin-report-purpose">{row.purpose}</td>
        <td>{DELIVERY_METHOD_LABELS[row.deliveryMethod]}</td><td>{formatRequestDate(row.requiredDate)}</td>
        <td><RequestStatusBadge status={row.status} /></td>
        <td>{formatRequestTime(row.createdAt)}</td><td>{formatRequestTime(row.updatedAt)}</td>
        <td>{row.completedAt ? formatRequestTime(row.completedAt) : '—'}</td>
      </tr>)}</tbody>
    </table>
  </div>
}

export function AdminReportsPage() {
  const [params, setParams] = useSearchParams()
  const query = params.toString()
  const applied = useMemo(() => readReportFilters(new URLSearchParams(query)), [query])
  const formKey = useMemo(() => { const copy = new URLSearchParams(query); copy.delete('page'); return copy.toString() }, [query])
  const filtered = Boolean(formKey)
  const [types, setTypes] = useState<AdminDocumentType[]>([])
  const [typesError, setTypesError] = useState<string | null>(null)
  const [result, setResult] = useState<PageResponse<RequestReportRow> | null>(null)
  const [state, setState] = useState<LoadState>('loading')
  const [error, setError] = useState<{ message: string; requestId?: string | null } | null>(null)
  const [reload, setReload] = useState(0)
  const loadedKey = useRef<string | null>(null)
  const [exporting, setExporting] = useState(false)
  const [exportError, setExportError] = useState<string | null>(null)
  const exportLock = useRef(false)

  useEffect(() => {
    const controller = new AbortController()
    async function loadTypes() {
      try {
        const all: AdminDocumentType[] = []
        let page = 0
        while (!controller.signal.aborted) {
          const batch = await listAdminDocumentTypes({ page, size: 100, sort: 'name', direction: 'asc' }, controller.signal)
          all.push(...batch.items)
          if (batch.last) break
          page++
        }
        if (!controller.signal.aborted) setTypes(all)
      } catch {
        if (!controller.signal.aborted) setTypesError('Document type choices could not be loaded. Other report filters remain available.')
      }
    }
    void loadTypes()
    return () => controller.abort()
  }, [])

  useEffect(() => {
    const controller = new AbortController()
    async function load() {
      const sameResults = loadedKey.current === query
      setState(sameResults ? 'refreshing' : 'loading')
      setError(null)
      try {
        const page = await listRequestReport(applied, controller.signal)
        if (controller.signal.aborted) return
        if ((page.totalPages > 0 && page.page >= page.totalPages) || (page.totalPages === 0 && page.page !== 0)) {
          setParams((prev) => appliedReportUrl({ ...readReportFilters(prev), page: Math.max(page.totalPages - 1, 0) }), { replace: true })
          return
        }
        setResult(page)
        loadedKey.current = query
        setState('ready')
      } catch (failure: unknown) {
        if (controller.signal.aborted) return
        setError({ message: getSafeErrorMessage(failure, 'Unable to load report.'), requestId: getApiError(failure)?.requestId })
        setState(sameResults ? 'stale' : 'error')
      }
    }
    void load()
    return () => controller.abort()
  }, [applied, reload, setParams, query])

  function applyFilters(next: RequestReportFilters) {
    const nextUrl = appliedReportUrl(next)
    setParams(nextUrl)
    setExportError(null)
    if (nextUrl.toString() === query) setReload((n) => n + 1)
  }

  function pageTo(nextPage: number) {
    setParams((previous) => appliedReportUrl({ ...readReportFilters(previous), page: nextPage }))
  }

  async function exportCsv() {
    if (exportLock.current) return
    exportLock.current = true
    setExporting(true)
    setExportError(null)
    try {
      // Uses exactly the same applied filters as the report; never unsaved form edits.
      await saveRequestReportCsv(applied)
    } catch (failure: unknown) {
      setExportError(getSafeErrorMessage(failure, 'Unable to export CSV. Please try again.'))
    } finally {
      exportLock.current = false
      setExporting(false)
    }
  }

  return <>
    <PageHeader eyebrow="Administration" title="Request reports"
      description="Filter submitted requests and export all matching rows as a safe UTF-8 CSV file, independent of the current page."
      actions={<button type="button" className="btn btn-primary" disabled={exporting || state === 'loading' || state === 'refreshing' || state === 'error'}
        onClick={() => void exportCsv()}>{exporting ? 'Preparing CSV...' : 'Export CSV'}</button>} />
    {typesError && <p role="status" className="small text-muted">{typesError}</p>}
    {exportError && <p role="alert" className="request-inline-error mb-3">{exportError}</p>}
    <ReportForm key={formKey} applied={applied} documentTypes={types}
      onApply={applyFilters} onClear={() => applyFilters({})} />
    <section className="surface-card request-results admin-report-results" aria-labelledby="report-results-heading">
      <div className="request-results-header"><div><p className="section-eyebrow mb-2">Request report</p>
        <h2 id="report-results-heading" className="h5 mb-0">Filtered results</h2></div>
        {result && (state === 'ready' || state === 'refreshing' || state === 'stale') && <span className="small text-muted">{result.totalItems} matching requests</span>}
      </div>
      {state === 'loading' && <PageState status="loading" message="Loading report rows..." />}
      {state === 'refreshing' && <p role="status" className="small text-muted px-4 pt-3 mb-0">Updating report…</p>}
      {state === 'stale' && <div className="px-4 pt-3" role="alert"><p className="request-inline-error">Unable to refresh. Previous report results remain visible. {error?.message}</p><button className="btn btn-outline-primary btn-sm" type="button" onClick={() => setReload((n) => n + 1)}>Try again</button></div>}
      {state === 'error' && <PageState status="error" title="Unable to load report" message={error?.message ?? 'Please retry.'}
        requestId={error?.requestId} onRetry={() => setReload((n) => n + 1)} />}
      {(state === 'ready' || state === 'refreshing' || state === 'stale') && result?.items.length === 0 && <PageState status="empty"
        title={filtered ? 'No matching requests' : 'No requests recorded'}
        message={filtered ? 'Adjust or clear your filters. CSV export will still produce a header row.' : 'Request reports become available when students submit requests.'} />}
      {(state === 'ready' || state === 'refreshing' || state === 'stale') && result && result.items.length > 0 && <>
        <div className="p-3"><ReportTable rows={result.items} /></div>
        <div className="request-results-pagination"><Pagination page={result} onPageChange={pageTo} /></div>
      </>}
    </section>
    <p className="small text-muted mt-3">CSV includes the same filtered records, across every page, with columns in the displayed order. Formula-looking cells are escaped.</p>
  </>
}
