import { useEffect, useRef, useState } from 'react'
import type { FormEvent } from 'react'
import type { PageResponse } from '../api/contracts'
import { getApiError, getSafeErrorMessage } from '../api/errors'
import { FeedbackBanner } from '../components/FeedbackBanner'
import type { FeedbackVariant } from '../components/FeedbackBanner'
import { PageHeader } from '../components/PageHeader'
import { PageState } from '../components/PageState'
import { Pagination } from '../components/Pagination'
import { DocumentTypeFormDialog, DocumentTypeStatusDialog } from './DocumentTypeDialogs'
import { getAdminDocumentType, listAdminDocumentTypes } from './documentTypesApi'
import type { AdminDocumentType, DocumentTypeFilters } from './documentTypesApi'
import './catalogue.css'

type Filters = { q: string; active: boolean | ''; sort: NonNullable<DocumentTypeFilters['sort']>; direction: NonNullable<DocumentTypeFilters['direction']> }
type LoadState = 'loading' | 'error' | 'ready' | 'refreshing' | 'stale'
type Feedback = { variant: FeedbackVariant; message: string; requestId?: string | null }
const DEFAULT_FILTERS: Filters = { q: '', active: '', sort: 'name', direction: 'asc' }
const dateFormatter = new Intl.DateTimeFormat(undefined, { day: 'numeric', month: 'short', year: 'numeric' })
function displayDate(value: string): string {
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? '—' : dateFormatter.format(date)
}

function DeliverySummary({ item }: { item: AdminDocumentType }) {
  return <span>{[item.electronicAvailable && 'Electronic', item.physicalAvailable && 'Physical'].filter(Boolean).join(' · ')}</span>
}

export function AdminDocumentTypesPage() {
  const [draftQ, setDraftQ] = useState('')
  const [filters, setFilters] = useState<Filters>(DEFAULT_FILTERS)
  const [page, setPage] = useState(0)
  const [reload, setReload] = useState(0)
  const [state, setState] = useState<LoadState>('loading')
  const [results, setResults] = useState<PageResponse<AdminDocumentType> | null>(null)
  const [loadError, setLoadError] = useState<{ message: string; requestId?: string | null } | null>(null)
  const [feedback, setFeedback] = useState<Feedback | null>(null)
  const [createOpen, setCreateOpen] = useState(false)
  const [editItem, setEditItem] = useState<AdminDocumentType | null>(null)
  const [statusItem, setStatusItem] = useState<AdminDocumentType | null>(null)
  const [openingEditId, setOpeningEditId] = useState<number | null>(null)
  const loadedKey = useRef<string | null>(null)
  const viewKey = JSON.stringify([filters, page])

  useEffect(() => {
    const controller = new AbortController()
    async function load() {
      const sameResults = loadedKey.current === viewKey
      setState(sameResults ? 'refreshing' : 'loading')
      setLoadError(null)
      try {
        const next = await listAdminDocumentTypes({ ...filters, page, size: 20 }, controller.signal)
        if (controller.signal.aborted) return
        if (next.totalPages > 0 && page >= next.totalPages) { setPage(next.totalPages - 1); return }
        setResults(next)
        loadedKey.current = viewKey
        setState('ready')
      } catch (failure: unknown) {
        if (!controller.signal.aborted) {
          setLoadError({ message: getSafeErrorMessage(failure, 'Unable to load document types.'), requestId: getApiError(failure)?.requestId })
          setState(sameResults ? 'stale' : 'error')
        }
      }
    }
    void load()
    return () => controller.abort()
  }, [filters, page, reload, viewKey])

  function refresh() { setReload((n) => n + 1) }
  function changeFilter<K extends keyof Filters>(name: K, value: Filters[K]) {
    setFilters((old) => ({ ...old, [name]: value }))
    setPage(0)
    setFeedback(null)
  }
  function applyQuery(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    changeFilter('q', draftQ.trim())
  }
  function clearFilters() {
    setDraftQ(''); setFilters({ ...DEFAULT_FILTERS }); setPage(0); setFeedback(null)
  }

  async function openEdit(id: number) {
    setOpeningEditId(id)
    setFeedback(null)
    try { setEditItem(await getAdminDocumentType(id)) }
    catch (failure: unknown) {
      setFeedback({ variant: 'error', message: getSafeErrorMessage(failure, 'Unable to load the latest document type details.'), requestId: getApiError(failure)?.requestId })
    } finally { setOpeningEditId(null) }
  }

  /** Never automatically resubmit a mutation whose response was lost. */
  function uncertainResult(id: number | null, code: string) {
    setFeedback({ variant: 'warning', message: 'Checking the current catalogue state. Do not repeat the action until its outcome is verified.' })
    refresh()
    if (id !== null) {
      void getAdminDocumentType(id).then((current) => {
        setFeedback({ variant: 'warning', message: `${current.name} is currently ${current.active ? 'active' : 'inactive'} on the server. Inspect its current details before trying again.` })
      }).catch(() => setFeedback({ variant: 'warning', message: 'The server state is still unavailable. Reconnect and refresh the list before trying again.' }))
    } else {
      void listAdminDocumentTypes({ q: code.trim(), size: 100 }).then((match) => {
        const found = match.items.find((item) => item.code === code.trim().toUpperCase())
        setFeedback({ variant: 'warning', message: found
          ? `${found.name} (${found.code}) exists on the server. Check its details before attempting to create it again.`
          : 'The entry could not be found in the current catalogue query. Verify the result when the connection is stable before retrying.' })
      }).catch(() => setFeedback({ variant: 'warning', message: 'Unable to recheck the entry. Reconnect and refresh before another attempt.' }))
    }
  }

  function saved(item: AdminDocumentType, operation: 'created' | 'updated') {
    setCreateOpen(false); setEditItem(null)
    if (operation === 'created') { setDraftQ(''); setFilters({ ...DEFAULT_FILTERS }); setPage(0) }
    setFeedback({ variant: 'success', message: `${item.name} was ${operation}. ${item.active ? 'It is available in the active catalogue.' : 'It remains inactive.'}` })
    refresh()
  }

  const filtered = Boolean(filters.q || filters.active !== '')

  return (
    <>
      <PageHeader eyebrow="Administration" title="Document types"
        description="Create, edit and control which documents students can request. Existing request records are never deleted."
        actions={<button type="button" className="btn btn-primary" onClick={() => { setFeedback(null); setCreateOpen(true) }}>Create document type</button>} />
      {feedback && <div className="mb-4"><FeedbackBanner variant={feedback.variant} message={feedback.message}
        requestId={feedback.requestId} onDismiss={() => setFeedback(null)} /></div>}
      <section className="surface-card admin-users-filter-card" aria-labelledby="catalogue-filter-heading">
        <div className="d-flex align-items-center justify-content-between gap-3 mb-3 flex-wrap">
          <h2 className="h5 mb-0" id="catalogue-filter-heading">Find document types</h2>
          <button type="button" className="btn btn-link btn-sm" onClick={clearFilters}>Clear all filters</button>
        </div>
        <div className="catalogue-admin-filters">
          <form onSubmit={applyQuery} className="catalogue-admin-search">
            <label className="form-label" htmlFor="catalogue-admin-query">Code or name</label>
            <div className="input-group">
              <input type="search" className="form-control" id="catalogue-admin-query" maxLength={100}
                value={draftQ} onChange={(event) => setDraftQ(event.target.value)} placeholder="Search document types" />
              <button type="submit" className="btn btn-outline-primary">Search</button>
            </div>
          </form>
          <div><label className="form-label" htmlFor="catalogue-admin-active">Visibility</label>
            <select className="form-select" id="catalogue-admin-active" value={filters.active === '' ? '' : String(filters.active)}
              onChange={(event) => changeFilter('active', event.target.value === '' ? '' : event.target.value === 'true')}>
              <option value="">All types</option><option value="true">Active</option><option value="false">Inactive</option>
            </select>
          </div>
          <div><label className="form-label" htmlFor="catalogue-admin-sort">Sort by</label>
            <select className="form-select" id="catalogue-admin-sort" value={filters.sort}
              onChange={(event) => changeFilter('sort', event.target.value as Filters['sort'])}>
              <option value="name">Name</option><option value="code">Code</option>
              <option value="createdAt">Created date</option><option value="active">Visibility</option>
            </select>
          </div>
          <div><label className="form-label" htmlFor="catalogue-admin-direction">Order</label>
            <select className="form-select" id="catalogue-admin-direction" value={filters.direction}
              onChange={(event) => changeFilter('direction', event.target.value as Filters['direction'])}>
              <option value="asc">Ascending</option><option value="desc">Descending</option>
            </select>
          </div>
        </div>
      </section>
      <section className="surface-card catalogue-admin-results" aria-labelledby="catalogue-results-heading">
        <div className="admin-users-results-heading">
          <div><h2 className="h5 mb-1" id="catalogue-results-heading">Catalogue entries</h2>
            <p className="small text-muted mb-0">{(state === 'ready' || state === 'refreshing' || state === 'stale') && results ? `${results.totalItems} matching document ${results.totalItems === 1 ? 'type' : 'types'}` : 'Active and inactive entries'}</p>
          </div>
          <button type="button" className="btn btn-outline-secondary btn-sm" disabled={state === 'loading' || state === 'refreshing'} onClick={refresh}>Refresh</button>
        </div>
        {state === 'loading' && <div className="admin-users-result-content"><PageState status="loading" message="Loading catalogue entries..." /></div>}
        {state === 'refreshing' && <p role="status" className="small text-muted px-4 mt-3 mb-0">Updating catalogue entries…</p>}
        {state === 'stale' && <div role="alert" className="px-4 mt-3"><p className="request-inline-error">Unable to refresh catalogue entries. The previous list remains visible. {loadError?.message}</p><button type="button" className="btn btn-outline-primary btn-sm" onClick={refresh}>Try again</button></div>}
        {state === 'error' && <div className="admin-users-result-content"><PageState status="error" message={loadError?.message ?? 'Unable to load document types.'}
          requestId={loadError?.requestId} onRetry={refresh} /></div>}
        {(state === 'ready' || state === 'refreshing' || state === 'stale') && results && results.items.length === 0 && <div className="admin-users-result-content">
          <PageState status="empty" title={filtered ? 'No matching document types' : 'Your catalogue is empty'}
            message={filtered ? 'Try a different code, name or visibility filter.' : 'Create the first document type to make it available for request selection.'}
            action={filtered ? <button type="button" className="btn btn-outline-primary" onClick={clearFilters}>Clear filters</button> :
              <button type="button" className="btn btn-primary" onClick={() => setCreateOpen(true)}>Create document type</button>} />
        </div>}
        {(state === 'ready' || state === 'refreshing' || state === 'stale') && results && results.items.length > 0 && <>
          <div className="catalogue-admin-table-scroll d-none d-lg-block">
            <table className="table catalogue-admin-table mb-0">
              <thead><tr><th scope="col">Document</th><th scope="col">Delivery</th><th scope="col">Supporting documents</th><th scope="col">Visibility</th><th scope="col">Created</th><th scope="col">Actions</th></tr></thead>
              <tbody>{results.items.map((item) => (
                <tr key={item.id}>
                  <td><strong className="d-block catalogue-break-text">{item.name}</strong><span className="catalogue-code">{item.code}</span></td>
                  <td><DeliverySummary item={item} /></td>
                  <td>{item.supportingDocumentsRequired ? 'Required' : 'Optional'}</td>
                  <td><span className={`admin-account-status ${item.active ? 'is-active' : 'is-inactive'}`}>{item.active ? 'Active' : 'Inactive'}</span></td>
                  <td>{displayDate(item.createdAt)}</td>
                  <td><div className="catalogue-actions">
                    <button type="button" className="btn btn-outline-primary btn-sm" disabled={openingEditId === item.id} onClick={() => void openEdit(item.id)}>
                      {openingEditId === item.id ? 'Loading...' : 'Edit'}</button>
                    <button type="button" className="btn btn-outline-secondary btn-sm" onClick={() => setStatusItem(item)}>{item.active ? 'Deactivate' : 'Activate'}</button>
                  </div></td>
                </tr>
              ))}</tbody>
            </table>
          </div>
          <div className="catalogue-admin-mobile d-lg-none">
            {results.items.map((item) => <article key={item.id} className="catalogue-admin-mobile-card">
              <p className="section-eyebrow mb-2 catalogue-break-text">{item.code}</p>
              <h3 className="h6 mb-3 catalogue-break-text">{item.name}</h3>
              <dl className="catalogue-admin-facts">
                <div><dt>Delivery</dt><dd><DeliverySummary item={item} /></dd></div>
                <div><dt>Supporting files</dt><dd>{item.supportingDocumentsRequired ? 'Required' : 'Optional'}</dd></div>
                <div><dt>Visibility</dt><dd>{item.active ? 'Active' : 'Inactive'}</dd></div>
                <div><dt>Created</dt><dd>{displayDate(item.createdAt)}</dd></div>
              </dl>
              <div className="catalogue-actions">
                <button type="button" className="btn btn-outline-primary btn-sm" disabled={openingEditId === item.id} onClick={() => void openEdit(item.id)}>
                  {openingEditId === item.id ? 'Loading...' : 'Edit'}</button>
                <button type="button" className="btn btn-outline-secondary btn-sm" onClick={() => setStatusItem(item)}>{item.active ? 'Deactivate' : 'Activate'}</button>
              </div>
            </article>)}
          </div>
          <div className="admin-users-pagination"><Pagination page={results} onPageChange={setPage} /></div>
        </>}
      </section>
      {createOpen && <DocumentTypeFormDialog onClose={() => setCreateOpen(false)} onSaved={saved} onUncertain={uncertainResult} />}
      {editItem && <DocumentTypeFormDialog key={editItem.id} existing={editItem} onClose={() => setEditItem(null)}
        onSaved={saved} onUncertain={uncertainResult} />}
      {statusItem && <DocumentTypeStatusDialog item={statusItem} onClose={() => setStatusItem(null)} onUncertain={uncertainResult}
        onChanged={(updated) => {
          setStatusItem(null)
          setFeedback({ variant: 'success', message: `${updated.name} is now ${updated.active ? 'active' : 'inactive'}. Existing requests are unchanged.` })
          refresh()
        }} />}
    </>
  )
}
